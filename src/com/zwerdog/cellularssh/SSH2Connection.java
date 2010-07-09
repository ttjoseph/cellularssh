package com.zwerdog.cellularssh;
import java.io.*;
import org.bouncycastle.BigInteger;
import org.bouncycastle.SecureRandom;
import javax.microedition.io.SocketConnection;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.generators.DHBasicKeyPairGenerator;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.params.DHKeyGenerationParameters;
import org.bouncycastle.crypto.params.DHParameters;
import org.bouncycastle.crypto.params.DHPrivateKeyParameters;
import org.bouncycastle.crypto.params.DHPublicKeyParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.util.encoders.Hex;


/**
 * Encapsulates an SSH2 connection.
 * 
 * @author Tom Joseph <ttjoseph@gmail.com>
 */
public class SSH2Connection /* TODO implements javax.microedition.io.Connection */
{
	private int receivedSequenceNumber_ = -1, sentSequenceNumber_ = -1;
	private DataInputStream remoteIn_;
	private DataOutputStream remoteOut_;
	private String host_, username_, password_, serverVersionString_;
	private int port_;
	private DHPublicKeyParameters dhMyPub_;
	private DHPrivateKeyParameters dhMyPriv_;
	private byte[] hostKey_;
	private BigInteger prime_, generator_, dhServerPub_, sharedSecret_;
	
	/** Saved for computing the exchange hash */
	private byte[] serverKexInitData_, clientKexInitData_;
	private byte[] exchangeHash_, sessionID_;
	private byte[] clientToServerInitVector_, clientToServerKey_, clientToServerHmacKey_;
	private byte[] serverToClientInitVector_, serverToClientKey_, serverToClientHmacKey_;
	private CBCBlockCipher aesEncryptor_, aesDecryptor_;
	
	/** Continuously reused to save on garbage collection. */
	private SSH2.Packet recvPacket_, sendPacket_;
	/** Lookup table for received packet handling */
	private PacketHandler[] packetHandlers_;
	private boolean authenticated_;
	public StatusListener listener_ = null;
	
	private ByteQueue receivedFromRemoteHostFifo_ = null;
	
	private int terminalWidth_ = 80, terminalHeight_ = 24, terminalCharWidth_ = 6, terminalCharHeight_ = 10;
	
	
	public interface StatusListener
	{
		public void onSSH2ConnectionEvent(int eventType, String message);
	}
	
	/**
	 * This type of IOException is thrown when an SSH2-specific error happens.
	 * For example, a user authentication failure.
	 */
	public class SSH2Exception extends IOException
	{
		/**
		 * Apparently important for serialization, even though nobody wants to serialize this class. 
		 */
		private static final long serialVersionUID = 1122223109738332199L;
		private String message_;
		public int reasonCode_;
		
		public SSH2Exception(int reasonCode, String message)
		{
			reasonCode_ = reasonCode;
			message_ = message;
		}
		
		/**
		 * Returns the reason why this exception happened.
		 */
		public String getMessage()
		{ return message_; }
		
		/**
		 * Returns reason code, which is probably an SSH2.MSG_* constant
		 */
		public int getReasonCode()
		{ return reasonCode_; }
	}
	
	/**
	 * All SSH2 packet handlers must implement this interface.
	 */
	private interface PacketHandler
	{
		/**
		 * Handles an SSH2 packet.
		 * 
		 * @param p
		 * @throws IOException
		 */
		public void handlePacket(SSH2.Packet p) throws IOException;
	}
	
	/**
	 * Begins key exchange process.
	 */
	private class Handle_KEXINIT implements PacketHandler
	{
		public void handlePacket(SSH2.Packet p) throws IOException
		{
			// TODO: Actually look through this list to make sure we support something on it
			// For now we'll assume hmac-sha1, aes128-cbc
			
			// Save the packet contents for later when we compute the exchange hash.
			// We include everything after the padlen field but before the padding.
			// This explicitly includes the packet type field, the first byte of the payload.
			p.rewind();
			// We subtract 1 because length includes the size of the padlen field itself
			int len = p.getInt() - p.getByte() - 1; // length - padlen - 1
			serverKexInitData_ = new byte[len];
			// Copy starting from after the padlen field
			System.arraycopy(p.data, 5, serverKexInitData_, 0, len);
			
			// Construct and send a SSH_MSG_KEXDH_REQUEST to ask for the
			// Diffie-Hellman group we want
			SSH2.Packet kexDHRequest = new SSH2.Packet();
			kexDHRequest.putByte(SSH2.MSG_KEXDH_REQUEST);
			kexDHRequest.putInt(SSH2.GROUP_SIZE_MIN); // Minimum size in bits of prime number
			kexDHRequest.putInt(SSH2.GROUP_SIZE_WANTED); // Preferred
			kexDHRequest.putInt(SSH2.GROUP_SIZE_MAX); // Max size
			sendPacket(kexDHRequest);
			
			packetHandlers_[SSH2.MSG_KEXDH_GEX_GROUP] = new Handle_KEX_DH_GEX();
		}
	}
	
	private class Handle_KEX_DH_GEX implements PacketHandler
	{
		public void handlePacket(SSH2.Packet p) throws IOException
		{
			switch(p.getType())
			{
			case SSH2.MSG_KEXDH_GEX_GROUP:
				prime_ = p.getBigInteger();
				generator_ = p.getBigInteger();
				printBigInteger("Prime", prime_);
				printBigInteger("Generator", generator_);

				// Now that we have the prime and generator, we can generate our DH key pair
				// and send our DH public key to the server.
				// XXX We are only generating 256 bits! I guess this isn't good enough?
				DHParameters dhParams = new DHParameters(prime_, generator_, null, 256);
				DHKeyGenerationParameters kgp = new DHKeyGenerationParameters(new SecureRandom(), dhParams);
				DHBasicKeyPairGenerator kpgen = new DHBasicKeyPairGenerator();
				kpgen.init(kgp);
				AsymmetricCipherKeyPair keyPair = kpgen.generateKeyPair();
				dhMyPub_ = (DHPublicKeyParameters) keyPair.getPublic();
				dhMyPriv_ = (DHPrivateKeyParameters) keyPair.getPrivate();

				SSH2.Packet gexInit = new SSH2.Packet();
				gexInit.putByte(SSH2.MSG_KEXDH_GEX_INIT);
				gexInit.putBigInteger(dhMyPub_.getY()); // Y is our DH public key
				sendPacket(gexInit);

				packetHandlers_[SSH2.MSG_KEXDH_GEX_GROUP] = null; // Don't accept this packet twice in a row
				packetHandlers_[SSH2.MSG_KEXDH_GEX_REPLY] = this;
				break;
			
			case SSH2.MSG_KEXDH_GEX_REPLY:
				// This packet contains host key, server's DH public key.
				// Now we can calculate the shared secret and exchange hash.
				hostKey_ = p.getByteString();
				// Get server DH public key that will be used to compute shared secret
				dhServerPub_ = p.getBigInteger();
				// Exchange hash signature is signature of the SHA1 hash of the concatenation of a bunch of stuff
				// XXX: We should verify this
				byte[] exchangeHashSignature = p.getByteString();

				// Compute shared secret
				BigInteger myX = dhMyPriv_.getX(); // client random number exponent
				sharedSecret_ = dhServerPub_.modPow(myX, prime_);

				// Compute exchange hash.  The first one of the session is also the session ID.
				exchangeHash_ = computeExchangeHash();
				if(sessionID_ == null)
				{
					sessionID_ = new byte[exchangeHash_.length];
					System.arraycopy(exchangeHash_, 0, sessionID_, 0, exchangeHash_.length);
				}

				// Debugging output
				System.err.println("Host key: ");
				Hex.encode(hostKey_, System.err);
				System.err.println();
				printBigInteger("Client DH public", dhMyPub_.getY());
				printBigInteger("Server DH public", dhServerPub_);
				printBigInteger("Shared secret", sharedSecret_);
				System.err.println("Exchange hash signature length is: " + exchangeHashSignature.length);

				packetHandlers_[SSH2.MSG_KEXDH_GEX_REPLY] = null;
				packetHandlers_[SSH2.MSG_NEWKEYS] = new Handle_NEWKEYS();
				break;
			}
		}
	}

	private class Handle_NEWKEYS implements PacketHandler
	{
		public void handlePacket(SSH2.Packet p) throws IOException
		{
			synchronized (sendPacket_)
			{
				// TODO: Verify that everything is really OK and we can start using the new keys
				// Tell the server we're ready to use the new keys
				sendPacket_.reset();
				sendPacket_.putByte(SSH2.MSG_NEWKEYS);
				sendPacket(sendPacket_);
			}
			// Now we can generate our keys and set up our received packet to use them
			generateKeys(); // This sets up the packet to use the new keys
			recvPacket_.setUseEncryption(true);
			sendPacket_.setUseEncryption(true);
			
			// Ask to authenticate the user
			// Only do this if we aren't authenticated already
			if(authenticated_ == false)
			{
				synchronized(sendPacket_)
				{
					sendPacket_.reset();
					sendPacket_.putByte(SSH2.MSG_SERVICE_REQUEST);
					sendPacket_.putString("ssh-userauth");
					sendPacket(sendPacket_);
				}
			}

			packetHandlers_[SSH2.MSG_SERVICE_ACCEPT] = new Handle_SERVICE_ACCEPT();
		}
	}
	
	private class Handle_SERVICE_ACCEPT implements PacketHandler
	{
		public void handlePacket(SSH2.Packet p) throws IOException
		{
			String service = p.getString();
			System.err.println("Handle_SERVICE_ACCEPT: Service accept: " + service);
			
			// If the server is cool with authenticating the user, give it the
			// authentication information
			if(service.equals("ssh-userauth"))
			{
				synchronized(sendPacket_)
				{
					sendPacket_.reset();
					sendPacket_.putByte(SSH2.MSG_USERAUTH_REQUEST);
					sendPacket_.putString(username_);
					sendPacket_.putString("ssh-connection");
					sendPacket_.putString("password");
					sendPacket_.putByte((byte) 0);
					sendPacket_.putString(password_);
					sendPacket(sendPacket_);
				}

				packetHandlers_[SSH2.MSG_USERAUTH_SUCCESS] 
				                = packetHandlers_[SSH2.MSG_USERAUTH_FAILURE]
				                = new Handle_USERAUTH();
			}

		}
	}
	
	/**
	 * Handle user authentication.
	 */
	private class Handle_USERAUTH implements PacketHandler
	{
		public void handlePacket(SSH2.Packet p) throws IOException
		{
			int type = p.getType();
			System.err.println("Handle_USERAUTH: Type is " + type);
			switch(p.getType())
			{
			case SSH2.MSG_USERAUTH_FAILURE:
				System.err.println("Handle_USERAUTH: Authentication failed. We could try " + p.getString());
				listener_.onSSH2ConnectionEvent(SSH2.MSG_USERAUTH_FAILURE, null);
				// XXX I guess we throw an exception here because this means the connection is not valid,
				// and merely telling a listener this is not strong enough
				throw new SSH2Exception(SSH2.MSG_USERAUTH_FAILURE, "User authentication failed.");
				
			case SSH2.MSG_USERAUTH_SUCCESS:
				System.err.println("Handle_USERAUTH: User authentication success!");
				authenticated_ = true;

				synchronized (sendPacket_)
				{
					// Open a channel for a shell
					sendPacket_.reset();
					sendPacket_.putByte(SSH2.MSG_CHANNEL_OPEN);
					sendPacket_.putString("session"); // We want a shell
					sendPacket_.putInt(0); // Arbitrarily chosen channel number
					sendPacket_.putInt(128 * 1024); // Initial window size, XXX: BlackBerry usually has a 128kB/connection limit!
					sendPacket_.putInt(4096); // Maximum packet size
					sendPacket(sendPacket_);
				}
				packetHandlers_[SSH2.MSG_CHANNEL_OPEN_CONFIRMATION] 
				                = packetHandlers_[SSH2.MSG_CHANNEL_OPEN_FAILURE] 
								= packetHandlers_[SSH2.MSG_CHANNEL_DATA] 
								= packetHandlers_[SSH2.MSG_CHANNEL_SUCCESS] 
								= packetHandlers_[SSH2.MSG_CHANNEL_FAILURE] 
				                = new Handle_CHANNEL();
				break;
			}
			System.err.println("Handle_USERAUTH done");
		}
	}
	

	/**
	 * Handles SSH channels.  So far we only support one of them.
	 */
	private class Handle_CHANNEL implements PacketHandler
	{
		public void handlePacket(SSH2.Packet p) throws IOException
		{
			switch(p.getType())
			{
			case SSH2.MSG_CHANNEL_OPEN_CONFIRMATION:
				System.err.println("Handle_CHANNEL: Channel opened.  Let's ask for a pty and shell.");
				
				synchronized (sendPacket_)
				{
					// Request a pty.  After this, we need to MSG_CHANNEL_REQUEST a shell
					sendPacket_.reset();
					sendPacket_.putByte(SSH2.MSG_CHANNEL_REQUEST);
					sendPacket_.putInt(0); // server channel XXX
					sendPacket_.putString("pty-req");
					sendPacket_.putByte((byte) 0); // "want reply" field.  We don't want one
					sendPacket_.putString("vt100"); // TERM variable
					sendPacket_.putInt(terminalWidth_); // Terminal width
					sendPacket_.putInt(terminalHeight_); // Terminal height
					sendPacket_.putInt(terminalWidth_ * terminalCharWidth_); // Terminal width in pixels
					sendPacket_.putInt(terminalHeight_ * terminalCharHeight_); // Terminal height in pixels
					sendPacket_.putString(""); // Terminal modes.  I don't know what to put here, so left blank
					sendPacket(sendPacket_);
					// We just did a pty-req, so let's ask for a shell
					sendPacket_.reset();
					sendPacket_.putByte(SSH2.MSG_CHANNEL_REQUEST);
					sendPacket_.putInt(0); // server channel
					sendPacket_.putString("shell");
					sendPacket_.putByte((byte) 0); // "want reply" field
					sendPacket(sendPacket_);
				}
				break;
			
			case SSH2.MSG_CHANNEL_OPEN_FAILURE:
				System.err.println("Handle_CHANNEL: Server doesn't want to open a channel!");
				throw new SSH2Exception(SSH2.MSG_CHANNEL_OPEN_FAILURE, "Channel open failure.");

			case SSH2.MSG_CHANNEL_DATA:
				p.getInt(); // Channel ID, I think.  For us it's always 0
				int dataLength = p.getInt();
				receivedFromRemoteHostFifo_.write(p.data, p.offset_, dataLength);				
				break;

			case SSH2.MSG_CHANNEL_SUCCESS:
				
				break;
			
			case SSH2.MSG_CHANNEL_FAILURE:
				System.err.println("Handle_CHANNEL: That last channel request failed.");
				throw new SSH2Exception(SSH2.MSG_CHANNEL_FAILURE, "Channel request failed.");
				
			case SSH2.MSG_CHANNEL_CLOSE:
				break;

			}
		}
	}

	/**
	 * The caller uses this to actually receive data through the connection. 
	 */
	private class SSH2InputStream extends InputStream
	{
		public SSH2InputStream()
		{
			// TODO: Check that we've done enough of the handshaking and authentication
			// that opening an input stream is actually a reasonable thing to do
			if(receivedFromRemoteHostFifo_ == null)
				receivedFromRemoteHostFifo_ = new ByteQueue(512);
			
			byte[] tmp = new byte[1];
			// Apparently you need to try to receive something before you send,
			// so we do it here so the caller doesn't have to worry about this.
			// This is because trying to receive forces the completion of
			// negotiation (KEX stuff), which needs to be done before you send
			// anything.
			try
			{
				read(tmp, 0, 0);
			} catch (IOException e)
			{
				System.err.println("SSH2InputStream(): Couldn't read anything to start with.");				
			}
			
		}
		
		public int read(byte[] b, int off, int len) throws IOException
		{
			// Guarantee that we either get at least one byte of data or throw an exception
			while (receivedFromRemoteHostFifo_.available() < 1)
			{
				// Received packet needs to know its sequence number to correctly
				// compute the MAC
				recvPacket_.setSequenceNumber(++receivedSequenceNumber_);
				// fromNetwork() will read at least an entire SSH2 packet
				recvPacket_.fromNetwork(remoteIn_);
				dispatchPacketHandler(recvPacket_);
			}
			
			// The packet handler added the data to the FIFO so let's get some
			return receivedFromRemoteHostFifo_.read(b, off, len);
		}

		/**
		 * This method is slow as hell; please don't use it.
		 */
		public int read() throws IOException
		{
			byte[] tmp = new byte[1];
			read(tmp, 0, 1);
			return tmp[0];
		}
	}
	
	/**
	 * An OutputStream that you can use to send data to the remote host. 
	 */
	private class SSH2OutputStream extends OutputStream
	{
		public SSH2OutputStream() {}
		
		public void write(byte[] b, int off, int len) throws IOException
		{
			synchronized(sendPacket_)
			{
				sendPacket_.reset();
				sendPacket_.putByte(SSH2.MSG_CHANNEL_DATA);
				sendPacket_.putInt(0); // Channel ID
				sendPacket_.putInt(len);
				sendPacket_.putBytes(b, off, len);
				sendPacket(sendPacket_);				
			}
		}
		
		/**
		 * Really slow way to send data - don't use.
		 */
		public void write(int b) throws IOException
		{
			byte[] tmp = new byte[1];
			tmp[0] = (byte) b;
			write(tmp, 0, 1);			
		}
		
	}
	
	private SSH2Connection() {}
	
	public SSH2Connection(String host, int port, String username, String password)
	{
		host_ = host;
		port_ = port;
		username_ = username;
		password_ = password;

		recvPacket_ = new SSH2.Packet();
		sendPacket_ = new SSH2.Packet();
		
		initPacketHandlers();
		
		// Add a do-nothing status listener by default so we don't have to
		// keep checking whether listener_ is null before using it
		listener_ = new StatusListener()
		{
			public void onSSH2ConnectionEvent(int eventType, String message)	{}
		};
	}
	
	public void setListener(StatusListener listener)
	{
		listener_ = listener;
	}
	
	/**
	 * Initialize packet handlers lookup table.
	 */
	private void initPacketHandlers()
	{
		// The size of this array is hardcoded because the packet type field is one byte, and that
		// isn't going to change without a major protocol revision
		packetHandlers_ = new PacketHandler[256];
		
		packetHandlers_[SSH2.MSG_KEXINIT] = new Handle_KEXINIT();
	}
	
	/**
	 * Set the dimensions of the terminal, because we have to report that to the server.
	 * Defaults are 80x24, char width 6 and height 10 pixels.
	 * 
	 * @param width
	 * @param height
	 * @param charWidth
	 * @param charHeight
	 */
	public void setTerminalDimensions(int width, int height, int charWidth, int charHeight)
	{
		terminalWidth_ = width;
		terminalHeight_ = height;
		terminalCharWidth_ = charWidth;
		terminalCharHeight_ = charHeight;
	}
	
	/**
	 * Returns an InputStream that you can use to read data from the server.
	 * 
	 * @return
	 * @throws IOException
	 */
	public InputStream openInputStream() throws IOException
	{
		return new SSH2InputStream();
	}
	
	/**
	 * Returns an OutputStream that you can use to send data to the server.
	 * 
	 * @return
	 * @throws IOException
	 */
	public OutputStream openOutputStream() throws IOException
	{
		return new SSH2OutputStream();
	}
	
	/**
	 * Opens an SSH2 connection over the SocketConnection specified in the constructor.
	 * We leave the opening of the socket connection itself to the caller because BlackBerry
	 * has a really brain-damaged hit-or-miss approach to opening a simple TCP connection
	 * and we don't want to make this class less generic by worrying about it.
	 * 
	 * @throws IOException
	 */
	public void open(SocketConnection remoteHost) throws IOException
	{
		// TODO: On BlackBerry we need to use Connector.open() instead
		//Socket s = new Socket(host_, port_);
//		SocketConnection remoteHost = (SocketConnection) Connector.open("socket://" + host_ + ":" + port_ + ";deviceside=true");
		remoteOut_ = remoteHost.openDataOutputStream();
		remoteIn_ = remoteHost.openDataInputStream();
		
		// The first things sent and received are the version strings, which
		// are not sent as SSH2 packets
		StringBuffer serverVersionString = new StringBuffer();
		byte b;
		while((b = (byte) remoteIn_.read()) != '\n')
			serverVersionString.append((char) b);
		serverVersionString_ = serverVersionString.toString().trim();
		System.err.println("Server ID string: " + serverVersionString);
		// TODO: Make sure the default character encoding is something with 1 byte to a character
		// so we can avoid the following nonsense of specifying the encoding we want every time
		remoteOut_.write(SSH2.CLIENT_VERSION_STRING.getBytes("ISO-8859-1"));
		remoteOut_.write("\n".getBytes("ISO-8859-1"));
		
		// Start things off by telling the server what ciphers we support
		SSH2.Packet clientKexInit = new SSH2.Packet();
		SSH2.becomeKexInitPacket(clientKexInit);
		clientKexInit.rewind();
		clientKexInit.skip(4 + 1 + 1 + 16); // length, padlen, type, cookie
		sendPacket(clientKexInit);
		
		// Save this payload because we'll need it later to calculate the exchange hash
		clientKexInit.rewind();
		// We subtract 1 to exclude the length of the padlen field
		// We explicitly want to include the packet type field right after the padlen field
		int len = clientKexInit.getInt() - clientKexInit.getByte() - 1; // length - padlen - 1
		clientKexInitData_ = new byte[len];
		// Copy starting from after the padlen field
		System.arraycopy(clientKexInit.data, 5, clientKexInitData_, 0, len);

	}
	
	/**
	 * Closes the connection with the remote host.
	 * 
	 * @throws IOException
	 */
	public void close() throws IOException
	{
		if(remoteOut_ != null)
			remoteOut_.close();
		if(remoteIn_ != null)
			remoteIn_.close();
	}
	
	/**
	 * Sends an SSH2 packet, taking care of the encryption and sequence number stuff.
	 * 
	 * @param p The packet to send
	 * @throws IOException
	 */
	private void sendPacket(SSH2.Packet p) throws IOException
	{
		System.err.println("sendPacket: Sending packet of type " + p.getTypeName());
		sentSequenceNumber_++;
		p.setSequenceNumber(sentSequenceNumber_);
		p.send(remoteOut_);
	}
	
	/**
	 * Dispatches a received packet to the appropriate handler.
	 * 
	 * @param p
	 * @throws IOException
	 */
	private void dispatchPacketHandler(SSH2.Packet p) throws IOException
	{
		p.rewind();
		p.skip(5); // Position pointer so next getByte will return packet type
		int packetType = p.getByte();
		int packetLength = p.getLengthField();
		
		if(packetHandlers_[packetType] != null)
		{
			//System.err.println("dispatchPacketHandler: Dispatching packet (type " + p.getTypeName() + ", length field " + packetLength 
			//		+ " to " + packetHandlers_[packetType].getClass().toString() + ")");
			packetHandlers_[packetType].handlePacket(p);
		}
		else
			System.err.println("dispatchPacketHandler: HEY! No handler was registered for packet type " + p.getTypeName());

		// Tell our listener that something happened
		listener_.onSSH2ConnectionEvent(packetType, null);
	}

	/**
	 * Generate keys (IV, encryption, MAC).
	 */
	private void generateKeys()
	{
		// Create IV and keys
		clientToServerInitVector_ = SSH2.generateKeyWithSHA1(sharedSecret_, exchangeHash_, (byte) 'A', sessionID_);
		clientToServerKey_ = SSH2.generateKeyWithSHA1(sharedSecret_, exchangeHash_, (byte) 'C', sessionID_);
		clientToServerHmacKey_ = SSH2.generateKeyWithSHA1(sharedSecret_, exchangeHash_, (byte) 'E', sessionID_);
		serverToClientInitVector_ = SSH2.generateKeyWithSHA1(sharedSecret_, exchangeHash_, (byte) 'B', sessionID_);
		serverToClientKey_ = SSH2.generateKeyWithSHA1(sharedSecret_, exchangeHash_, (byte) 'D', sessionID_);
		serverToClientHmacKey_ = SSH2.generateKeyWithSHA1(sharedSecret_, exchangeHash_, (byte) 'F', sessionID_);
		
		//System.err.print("HMAC key:");
		//hexDump(clientToServerHmacKey);
		//System.err.print("serverToClientKey:");
		//hexDump(serverToClientKey);
		
		// For now we only support AES-128
		aesEncryptor_ = new CBCBlockCipher(new AESEngine());
		// The encryption key is a hash of the concatenation of some stuff
		KeyParameter serverKeyParam = new KeyParameter(clientToServerKey_, 0, 128/8);
		aesEncryptor_.init(true, new ParametersWithIV(serverKeyParam, clientToServerInitVector_, 0, 128/8));
		aesDecryptor_ = new CBCBlockCipher(new AESEngine());
		KeyParameter serverToClientKeyParam = new KeyParameter(serverToClientKey_, 0, 128/8);
		aesDecryptor_.init(false, new ParametersWithIV(serverToClientKeyParam, serverToClientInitVector_, 0, 128/8));
		recvPacket_.setDecryptor(aesDecryptor_);
		recvPacket_.setDecryptionHMac(new HMac(new SHA1Digest()), serverToClientHmacKey_);

		// TEMPORARY
		recvPacket_.setEncryptor(aesEncryptor_);
		recvPacket_.setEncryptionHMac(new HMac(new SHA1Digest()), clientToServerHmacKey_);

		sendPacket_.setEncryptor(aesEncryptor_);
		sendPacket_.setEncryptionHMac(new HMac(new SHA1Digest()), clientToServerHmacKey_);
		
	}

	/**
	 * Computes the SSH2 exchange hash.
	 * This is composed of version strings, KEXINIT payloads, host key, DH key exchange stuff, shared secret.
	 * 
	 * @return The exchange hash
	 */
	private byte[] computeExchangeHash()
	{
		// TODO: Assert that all these variables are not null.  If they are null, drop the connection
		// and tell the user something went wrong.
		// They could be null if host or we screwed up the protocol and we are trying to
		// calculate this earlier than we should be, before all this information was collected
		
		SSH2.Packet tmp = new SSH2.Packet();
		// Client version string
		tmp.putString(SSH2.CLIENT_VERSION_STRING);
		// Server version string
		tmp.putString(serverVersionString_.toString());
		// payload of client's SSH_MSG_KEXINIT
		tmp.putInt(clientKexInitData_.length);
		tmp.putBytes(clientKexInitData_);
		// payload of server's SSH_MSG_KEXINIT
		tmp.putInt(serverKexInitData_.length);
		tmp.putBytes(serverKexInitData_);

		// server host key
		tmp.putInt(hostKey_.length);
		tmp.putBytes(hostKey_);
		// minimum, preferred, maximum group size (each one is 4 bytes)
		tmp.putInt(SSH2.GROUP_SIZE_MIN);
		tmp.putInt(SSH2.GROUP_SIZE_WANTED);
		tmp.putInt(SSH2.GROUP_SIZE_MAX);
		
		// prime and generator
		tmp.putBigInteger(prime_);
		tmp.putBigInteger(generator_);
		// client's DH public key
		tmp.putBigInteger(dhMyPub_.getY());
		// server's DH public key
		tmp.putBigInteger(dhServerPub_);
		// shared secret
		tmp.putBigInteger(sharedSecret_);
		
		// DEBUG: dump exchange block that will be hashed
		/*byte[] debug_ehsrc = new byte[tmp.getLength() - 5]; 
		System.arraycopy(tmp.data, 5, debug_ehsrc, 0, tmp.getLength() - 5);
		System.err.println("***** EXCHANGE HASH SOURCE *****");
		hexDump(debug_ehsrc);*/
		
		return tmp.toExchangeHash();
	}
	
	/**
	 * Prints a BigInteger in hex to stderr, for debugging.
	 * 
	 * @param msg Arbitrary text so user knows what was printed
	 * @param n
	 */
	private static void printBigInteger(String msg, BigInteger n)
	{
		System.err.print(msg + ": ");
		try { Hex.encode(n.toByteArray(), System.err); } catch (IOException e) {}
		System.err.println();
	}
	
	/**
	 * For debugging, hex dumps the contents of a byte array.
	 * 16 bytes per line, in groups of two bytes (four hex digits)
	 * 
	 * @param b
	 * @throws IOException 
	 */
	public static void hexDump(byte[] b, int off, int len) throws IOException
	{
		for(int i = 0; i < len; i += 2)
		{
			if(i % 16 == 0)
				System.err.println();

			if(i < (len - 1))
				Hex.encode(b, off + i, 2, System.err);
			else
				Hex.encode(b, off + i, 1, System.err);
			
			System.err.print(' ');
		}
		System.err.println();
	}


}
