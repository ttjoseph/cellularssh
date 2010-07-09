package com.zwerdog.cellularssh;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.bouncycastle.BigInteger;
import java.util.Random;

import net.rim.device.api.system.DeviceInfo;

import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;

/**
 * Some helper stuff for implementing the SSH2 protocol.
 * 
 * @author Tom Joseph <ttjoseph@gmail.com>
 * @see SSH2Connection
 */
public class SSH2
{
	public static final byte MSG_DISCONNECT = 1;
	public static final byte MSG_IGNORE = 2;
	public static final byte MSG_UNIMPLEMENTED = 3;
	public static final byte MSG_DEBUG = 4;
	public static final byte MSG_SERVICE_REQUEST = 5;
	public static final byte MSG_SERVICE_ACCEPT = 6;
	public static final byte MSG_KEXINIT = 20;
	public static final byte MSG_NEWKEYS = 21;
	public static final byte MSG_KEXDH_GEX_REPLY = 33;
	public static final byte MSG_KEXDH_REQUEST = 34;
	public static final byte MSG_KEXDH_GEX_GROUP = 31;
	public static final byte MSG_KEXDH_GEX_INIT = 32;
	public static final byte MSG_USERAUTH_REQUEST = 50;
	public static final byte MSG_USERAUTH_FAILURE = 51;
	public static final byte MSG_USERAUTH_SUCCESS = 52;
	public static final byte MSG_CHANNEL_OPEN = 90;
	public static final byte MSG_CHANNEL_OPEN_CONFIRMATION = 91;
	public static final byte MSG_CHANNEL_OPEN_FAILURE = 92;
	public static final byte MSG_CHANNEL_WINDOW_ADJUST = 93;
	public static final byte MSG_CHANNEL_DATA = 94;
	public static final byte MSG_CHANNEL_EOF = 96;
	public static final byte MSG_CHANNEL_CLOSE = 97;
	public static final byte MSG_CHANNEL_REQUEST = 98;
	public static final byte MSG_CHANNEL_SUCCESS = 99;
	public static final byte MSG_CHANNEL_FAILURE = 100;
	
	public static final String CLIENT_VERSION_STRING = "SSH-2.0-CellularSSH";
	// The minimum here is 1024, or OpenSSH won't like it
	public static final int GROUP_SIZE_MIN = 1024, GROUP_SIZE_WANTED = 1024, GROUP_SIZE_MAX = 1024;
	
	public static final Random random_ = new Random(DeviceInfo.getDeviceId() * (DeviceInfo.getIdleTime() % 16));
	
	/**
	 * Encapsulates an SSH2 packet.
	 * @author tom
	 *
	 */
	public static class Packet
	{
		private String[] typeNames_ = new String[256];
		
		public byte[] data = null, encryptedData_, macData_, computedMac_;
		private int realLength_, sequenceNum_ = -1;
		private boolean blessed_, useEncryption_;
		private CBCBlockCipher encryptor_ = null, decryptor_ = null;
		private HMac encryptionHMac_, decryptionHMac_;
		
		/** Don't manipulate this directly! It's only public so reading it is fast. */
		public int offset_;
		
		/**
		 * Set this to true if you want to use encryption.
		 * 
		 * @param enabled
		 */
		public void setUseEncryption(boolean enabled)
		{
			//assert(encryptor_ != null || decryptor_ != null);
			if(encryptor_ != null || decryptor_ != null)
				useEncryption_ = enabled;
			else
				System.err.println("SSH2.Packet.setUseEncryption: Trying to use encryption but no encryptor or decryptor is set!");
		}
				
		/**
		 * Sets the SSH packet sequence number.  This will be incremented after the packet
		 * is sent.
		 * 
		 * @param num
		 */
		public void setSequenceNumber(int num)
		{
			sequenceNum_ = num;
		}
		
		/**
		 * Returns the SSH sequence number.
		 * 
		 * @returns The number
		 */
		public int getSequenceNumber()
		{
			return sequenceNum_;
		}
		
		/**
		 * Set the encryption scheme to use; once this is set the bless() method will
		 * automatically start encrypting this packet.
		 * 
		 * @param encryptor
		 */
		public void setEncryptor(CBCBlockCipher encryptor)
		{
			encryptor_ = encryptor;
		}
		
		/**
		 * Returns length of packet as it will be sent, minus MAC, which is added on during bless().
		 * 
		 * @return
		 */
		public int getLength()
		{
			return realLength_;
		}
		
		/**
		 * Increments pointer by some amount of bytes.
		 * 
		 * @param n Some amount of bytes
		 */
		public void skip(int n)
		{
			offset_ += n;
		}
		
		/**
		 * Populates this packet from network input, decrypting if necessary.
		 * There is no static method to do this, because this way you can reuse the same SSH2.Packet
		 * for multiple received packets, and it will keep track of the sequence number, crypto,
		 * and suchlike.
		 * 
		 * @param in Where to get the packet from.
		 * @throws IOException
		 */
		public void fromNetwork(DataInputStream in) throws IOException
		{
			reset();
			
			// Get an unencrypted packet
			if(useEncryption_ == false || decryptor_ == null)
			{
				if(in == null)
					System.err.println("SSH2.Packet.fromNetwork: I was given a null DataInputStream!");
				int length = in.readInt();
				int paddingLength = (int) in.readByte();
				// The packet type will determine whether there is a MAC
				byte packetType = in.readByte();

				offset_ = 0;
				putInt(length);
				putByte((byte) paddingLength);
				putByte(packetType);

				// Read everything except the MAC.
				// Subtract two because of padding length and packet type fields
				ensureSize(length + 4); // XXX: add MAC size to this
				in.readFully(data, offset_, length - 2);
				realLength_ += length - 2;

				// Reposition offset at packet type field
				offset_--;
			} else // We expect the packet to be encrypted, because the caller has given us a decryptor
			{
				if(encryptedData_ == null)
					encryptedData_ = new byte[data.length];
				
				// Get first block so we can decrypt it and read the length field, so we know
				// how many more blocks to fetch (if any).
				int blockSize = decryptor_.getBlockSize();
				in.readFully(encryptedData_, 0, blockSize);

//				System.err.print("fromNetwork: First encrypted block of this packet is:");
//				Main.hexDump(encryptedData_, 0, blockSize);
				
				decryptor_.processBlock(encryptedData_, 0, data, 0);
				offset_ = 0;

//				System.err.print("fromNetwork: First decrypted block of this packet is:");
//				Main.hexDump(data, 0, blockSize);

				int length = getInt();
				getByte();
				/* byte type = */ getByte();
				if(length < 5 || length > 32768)
				{
					System.err.println("fromNetwork: Packet length " + length + " is fishy. Something's gone wrong.");
					throw new IOException();
				}
				
				// Resize data buffers if necessary
				if(encryptedData_.length < length)
				{
					encryptedData_ = biggerBuffer(encryptedData_, length * 2);
					// biggerBuffer() copies the old data over.  We only need to copy the first
					// block, so we avoid copying the rest of it by replacing the buffer manually
					byte[] data2 = new byte[length * 2];
					System.arraycopy(data, 0, data2, 0, blockSize);
					data = data2;
				}
				
				int numBlocksLeft = (length + 4) / blockSize - 1;
				// System.err.println("fromNetwork: There are " + numBlocksLeft + " blocks left to read, not including the MAC");
				in.readFully(encryptedData_, blockSize, numBlocksLeft * blockSize);
				for(int i = 1; i <= numBlocksLeft; i++)
					decryptor_.processBlock(encryptedData_, i * blockSize, data, i * blockSize);
				
				// Read the MAC.
				if(macData_ == null)
					macData_ = new byte[decryptionHMac_.getMacSize()];
				
				// System.err.println("fromNetwork: Reading mac of size " + macData_.length);
				in.readFully(macData_, 0, macData_.length);	
				
				if(computedMac_ == null)
					computedMac_ = new byte[macData_.length];

				// MAC is M(key, seqno || data) where M is some hash function
				decryptionHMac_.update(intToByteArray(sequenceNum_), 0, 4);
				decryptionHMac_.update(data, 0, blockSize + blockSize * numBlocksLeft);
				decryptionHMac_.doFinal(computedMac_, 0);
				// decryptionHMac_.reset(); // ??
				
				//System.err.println("Computed MAC:");
				//SSH2Connection.hexDump(computedMac_, 0, computedMac_.length);
				//System.err.println("Received MAC:");
				//SSH2Connection.hexDump(macData_, 0, macData_.length);
				
				// Compare the computed and received MACs - if they don't match, the connection has been messed up
				for(int i = 0; i < computedMac_.length; i++)
				{
					if(computedMac_[i] != macData_[i])
						throw new IOException("Connection is messed up (MACs do not match)");
				}
			}
			
			//offset_ = 0;
			//int length = getInt();
			//int paddingLength = getByte();
			//int packetType = getByte();
			//offset_--;
			// System.err.println("fromNetwork: length = " + length + " padding = " + paddingLength + " type = " + packetType);
			offset_ = 5; // Point at packet type
			
			blessed_ = true; // Packets received from the network are already blessed
		}
		
		/**
		 * Bless and send this packet.
		 * 
		 * @param out Where to send the packet
		 * @throws IOException
		 * @see bless
		 */
		public void send(DataOutputStream out) throws IOException
		{
			bless();
			
			if(useEncryption_ == false || encryptor_ == null)
				out.write(data, 0, realLength_);
			else
			{
//				System.err.println("send: [" + sequenceNum_ + "] Sending encrypted data of length " + realLength_ + " and MAC length " + macData_.length);
				// Send encrypted data
				out.write(encryptedData_, 0, realLength_);
				// Send MAC
				out.write(macData_, 0, macData_.length);
//				System.err.print("      MAC is");
//				Main.hexDump(macData_);
			}
		}
		
		/**
		 * Gets the SSH2 length field (first four bytes) without changing the offset pointer.
		 * @return
		 */
		public int getLengthField()
		{
			int oldOffset = offset_;
			offset_ = 0;
			int ret = getInt();
			offset_ = oldOffset;
			return ret;
		}
		
		/**
		 * Returns the SSH type field (offset 5) of the packet without changing the
		 * offset pointer.
		 * 
		 * @return
		 */
		public int getType()
		{
			int oldOffset = offset_;
			offset_ = 5;
			int ret = getByte();
			offset_ = oldOffset;
			return ret;
		}
		
		/**
		 * Returns a string describing the type of this packet.
		 * 
		 * @return
		 */
		public String getTypeName()
		{
			String name = typeNames_[getType()];
			if(name == null)
				name = "(unknown packet type " + getType() + ")";
			return name;
		}
		
		/**
		 * Initializes an empty packet without encryption turned on.
		 * We expect that a new Packet won't be instantiated for every packet sent and
		 * that the caller will choose to reset() an existing packet.
		 */
		public Packet()
		{
			// Set up packet type names table for the use of getTypeName()
			typeNames_[1] = "DISCONNECT";
			typeNames_[2] = "IGNORE";
			typeNames_[3] = "UNIMPLEMENTED";
			typeNames_[4] = "DEBUG";
			typeNames_[5] = "SERVICE_REQUEST";
			typeNames_[6] = "SERVICE_ACCEPT";
			typeNames_[20] = "KEXINIT";
			typeNames_[21] = "NEWKEYS";
			typeNames_[33] = "KEXDH_GEX_REPLY";
			typeNames_[34] = "KEXDH_REQUEST";
			typeNames_[31] = "KEXDH_GEX_GROUP";
			typeNames_[32] = "KEXDH_GEX_INIT";
			typeNames_[50] = "USERAUTH_REQUEST";
			typeNames_[51] = "USERAUTH_FAILURE";
			typeNames_[52] = "USERAUTH_SUCCESS";
			typeNames_[90] = "CHANNEL_OPEN";
			typeNames_[91] = "CHANNEL_OPEN_CONFIRMATION";
			typeNames_[92] = "CHANNEL_OPEN_FAILURE";
			typeNames_[93] = "CHANNEL_WINDOW_ADJUST";
			typeNames_[94] = "CHANNEL_DATA";
			typeNames_[96] = "CHANNEL_EOF";
			typeNames_[97] = "CHANNEL_CLOSE";
			typeNames_[98] = "CHANNEL_REQUEST";
			typeNames_[99] = "CHANNEL_SUCCESS";
			typeNames_[100] = "CHANNEL_FAILURE";
			
			// The "data" field is public to avoid the overhead of accessors
			data = new byte[512];
			realLength_ = offset_ = 5; // Need to leave 4 bytes for total length and 1 byte for padding length
		}
		
		/**
		 * Clears the packet without changing the encryption settings, making it ready for more data.
		 */
		public void reset()
		{
			realLength_ = 0;
			offset_ = 0;
			blessed_ = false;
			putInt(0); // Length
			putByte((byte) 0); // Padding length
			// At this point, offset_ == realLength_ == 5
		}
		
		/**
		 * Resets pointer to start of packet data.
		 */
		public void rewind()
		{
			offset_ = 0;
		}
		
		/**
		 * Adds the surrounding SSH2 packet stuff so it can be sent over the wire, encrypting if necessary.
		 * Assumes that realLength_ is the offset of where the padding should start.
		 * Cannot bless a packet twice, that will corrupt it, so this doesn't allow it!
		 */
		public void bless()
		{
			if(blessed_)
				return;
			
			int blockSize;
			
			// XXX: block size should be less than 256
			if(useEncryption_ == true && encryptor_ != null)
				blockSize = encryptor_.getBlockSize();
			else
				blockSize = 8;

			// LENGTH includes length of PADLEN, DATA, PADDING
			// PADLEN includes length of PADDING
			// length_of_DATA is LENGTH - PADLEN
			// PADLEN ensures that length_of_DATA + 5 is multiple of 8 
			// There is no MAC, so we just round up.  Padding has to be 4-255 bytes.
			int padFrom = realLength_;
			int paddingLength = blockSize - (padFrom % blockSize);
			if(paddingLength < 4)
				paddingLength += blockSize;

//			System.err.println("bless: Padded using block size " + blockSize);
//			System.err.println("bless: padFrom = " + padFrom);
//			System.err.println("bless: paddingLength = " + paddingLength);
//			System.err.println("bless: length = " + (padFrom + paddingLength));

			// Length field is realSize + paddingLength - 4
			// We subtract the size of the length field itself
			int length = padFrom + paddingLength - 4;
			offset_ = 0;
			putInt(length);
			putByte((byte) (paddingLength));
			// length + 4 is data and padding plus size of length field itself
			ensureSize(length + 4);
			realLength_ = length + 4;

			// Now we're done padding, so we are of a size friendly to the block cipher
			// If we have an encryptor, use it
			if(useEncryption_ == true && encryptor_ != null)
			{
				// TODO: Fill padding with random data!
				
				// At this point the data to encrypt should be a multiple of the block size
				int numBlocks = realLength_ / blockSize;
//				System.err.println("bless: " + realLength_ + " bytes of data to encrypt, in " + numBlocks
//						+ " blocks of " + blockSize + " bytes each");

				// We compute the MAC before we encrypt the data
				if(encryptionHMac_ == null)
				{
					System.err.println("bless: Encryption is on and I have no way to make a MAC. This is a bug.");
					return;
				}
				
				if(macData_ == null)
					macData_ = new byte[encryptionHMac_.getMacSize()];
				
				// MAC is M(key, seqno || unenc_msg) where M is MAC function
				encryptionHMac_.update(SSH2.intToByteArray(sequenceNum_), 0, 4);
				encryptionHMac_.update(data, 0, realLength_);
				encryptionHMac_.doFinal(macData_, 0); // Get MAC data out
				
				// Encrypt the data
				if(encryptedData_ == null || encryptedData_.length < realLength_)
					encryptedData_ = new byte[realLength_];
				
				for(int i = 0; i < numBlocks; i++)
					encryptor_.processBlock(data, i * blockSize, encryptedData_, i * blockSize);
			}			
			
			blessed_ = true;
		}
		
		/**
		 * Makes sure the data array is big enough.  If not, copies it into a new one that is
		 * big enough. Ideally this is not called very often because you only need one Packet
		 * per stream (e.g. one each for input and output) and can reuse it.
		 * 
		 * @param b
		 * @param size
		 * @return
		 */
		public void ensureSize(int size)
		{
			if(data.length < size)
			{
				// We double the array size so we don't have to keep resizing a lot
				byte[] biggerData = new byte[size * 2];
				System.arraycopy(data, 0, biggerData, 0, realLength_);
				data = biggerData;
			}
		}
		
		/**
		 * Puts a byte at the current offset.
		 * 
		 * @param b Byte to put
		 * @return How many bytes were put, which is always 1
		 */
		public int putByte(byte b)
		{
			ensureSize(offset_ + 1);
			
			data[offset_++] = b;
			
			if(realLength_ < offset_)
				realLength_ = offset_;
			
			return 1;
		}
		
		/**
		 * Put an integer in big-endian (network byte order) format.
		 * 
		 * @param n Integer put
		 * @returns Number of bytes used in out buffer (always four)
		 */
		public int putInt(int n)
		{
			ensureSize(offset_ + 4);
			
			data[offset_] = (byte) ((n >> 24) & 0xff);
			data[offset_ + 1] = (byte) ((n >> 16) & 0xff);
			data[offset_ + 2] = (byte) ((n >> 8) & 0xff);
			data[offset_ + 3] = (byte) (n & 0xff);
			
			offset_ += 4;
			if(realLength_ < offset_)
				realLength_ = offset_;
				
			return 4;
		}
		
		/**
		 * Get an integer from the packet.  All these integers are stored big-endian
		 * (network order) in the packet but are returned in a usable state.
		 * 
		 * @return
		 */
		public int getInt()
		{
			int ret = (data[offset_] << 24)
	        	| ((data[offset_ + 1] & 0xFF) << 16)
	        	| ((data[offset_ + 2] & 0xFF) << 8)
	        	| (data[offset_ + 3] & 0xFF);
			
			offset_ += 4;
			return ret;
		}
		
		/**
		 * Gets a short (16 bits) from the offset, and moves to the next position.
		 * 
		 * @return The short
		 */
		public short getShort()
		{
			short ret = (short) ((data[offset_] << 8) | (data[offset_ + 1] & 0xFF));
			offset_ += 2;
			return ret;
		}
		
		/**
		 * Gets the byte at the current position and increments the pointer.
		 * 
		 * @return The byte
		 */
		public byte getByte()
		{
			return data[offset_++];
		}
		
		/**
		 * Gets a certain number of bytes at the current position and nudges the pointer.
		 * 
		 * @param length How many bytes to get
		 * @param buf Where to put the bytes
		 * @return The buf parameter given by the caller, where the bytes were put
		 */
		public byte[] getBytes(int length, byte[] buf)
		{
			System.arraycopy(data, offset_, buf, 0, length);
			offset_ += length;
			return buf;
		}
		
		/**
		 * Gets a certain number of bytes, allocating a new buffer for them.
		 * 
		 * @param length How many bytes to get
		 * @return The bytes that were gotten
		 */
		public byte[] getBytes(int length)
		{
			return getBytes(length, new byte[length]);
		}
		
		/**
		 * Get some bytes (length field must be first).
		 * How many bytes is expected to be in a 32-bit int at the current offset, and the bytes
		 * themselves immediately after.
		 * 
		 * @return The bytes
		 */
		public byte[] getByteString()
		{
			int length = getInt();
			return getBytes(length, new byte[length]);
		}
		
		/**
		 * Put bytes, and don't put a length field.
		 * 
		 * @param b Buffer with the bytes in them
		 * @param off Where the bytes start in the buffer
		 * @param len How many bytes
		 * @return How many bytes were put (same number as length parameter)
		 */
		public int putBytes(byte[] b, int off, int len)
		{
			ensureSize(offset_ + len);
			System.arraycopy(b, off, data, offset_, len);
			offset_ += len;
			if(realLength_ < offset_)
				realLength_ = offset_;

			return len;
		}
		
		/**
		 * Puts all the bytes in b into this packet at the current position.
		 * 
		 * @param b The bytes to put
		 * @return Number of bytes put (b.length)
		 */
		public int putBytes(byte[] b)
		{
			return putBytes(b, 0, b.length);
		}
		
		/**
		 * Gets a BigInteger, which is a big-endian twos-complement integer of an arbitrary length,
		 * preceded by a 32-bit integer that tells how long it is.
		 * 
		 * @return The BigInteger that was gotten
		 */
		public BigInteger getBigInteger()
		{
			int length = getInt();
			return new BigInteger(getBytes(length));
		}
		
		/**
		 * Puts a BigInteger into this packet at the current position.
		 * 
		 * @param val The BigInteger to put
		 * @return How many bytes were put
		 * @see getBigInteger
		 */
		public int putBigInteger(BigInteger val)
		{
			byte[] buf = val.toByteArray();
			putInt(buf.length);
			putBytes(buf, 0, buf.length);
			return buf.length + 4;
		}
		
		/**
		 * Put a string into the packet at the position pointed to by internal
		 * offset pointer.
		 * 
		 * @param s
		 * @return Number of bytes added to packet
		 */
		public int putString(String s)
		{
			ensureSize(offset_ + 4 + s.length());
			
			putInt(s.length());
			
			// We don't use String.getBytes because the local encoding may not be
			// one byte per character.  This approach would break on multi-byte
			// chars (if such things exist) but at least it's slightly better
			for(int i = 0; i < s.length(); i++)
				data[offset_++] = (byte) s.charAt(i);
			
			if(realLength_ < offset_)
				realLength_ = offset_;
			
			return s.length();
		}
		
		/**
		 * Offset should point at a length field followed by a string of that many
		 * bytes.
		 * @return
		 */
		public String getString()
		{
			int length = getInt();
			String s = new String(data, offset_, length);
			offset_ += length;
			return s;		
		}
		
		/**
		 * Assume everything after the length and padlen fields is part of the exchange hash
		 * and compute it.
		 */
		public byte[] toExchangeHash()
		{
			SHA1Digest digest = new SHA1Digest();
			byte[] digestValue = new byte[digest.getDigestSize()];
			digest.update(data, 5, realLength_ - 5);
			digest.doFinal(digestValue, 0);
			return digestValue;
		}

		/**
		 * Encrypted sent packets have a MAC at the end. This function lets you set the
		 * HMAC you want, allowing for the possibility of different hashes (e.g. MD5, SHA-1)
		 * 
		 * @param mac
		 * @param hmacKey
		 */
		public void setEncryptionHMac(HMac mac, byte[] hmacKey)
		{
			encryptionHMac_ = mac;
			KeyParameter kp = new KeyParameter(hmacKey);
			encryptionHMac_.init(kp);
		}
		
		/**
		 * Sets the HMAC for decrypting incoming packets.
		 * 
		 * @see setEncryptionHMac
		 * @param mac
		 * @param hmacKey
		 */
		public void setDecryptionHMac(HMac mac, byte[] hmacKey)
		{
			decryptionHMac_ = mac;
			KeyParameter kp = new KeyParameter(hmacKey);
			decryptionHMac_.init(kp);
		}

		/**
		 * Provides an object that will decrypt this packet.
		 * 
		 * @param decryptor
		 */
		public void setDecryptor(CBCBlockCipher decryptor)
		{
			decryptor_ = decryptor;			
		}
		
	} // End of Packet definition
	
	/**
	 * Makes a new buffer and copies the contents of the old one into it.
	 * If the requested length is less than the current length of the buffer, just
	 * returns the old buffer.
	 * 
	 * @param b Buffer whose data should be copied
	 * @return New buffer, with contents of b in it starting from 0
	 */
	public static byte[] biggerBuffer(byte[] b, int len)
	{
		if(len < b.length)
			return b;
		byte[] b2 = new byte[len];
		System.arraycopy(b, 0, b2, 0, b.length);
		return b2;
	}

	/**
	 * Generates a 128-bit key (or IV) for SSH2 use: IV, encryption key, MAC key - using the SHA-1 hash.
	 * 
	 * 'A': client-to-server IV
	 * 'B': server-to-client IV
	 * 'C': client-to-server encryption
	 * 'D': server-to-client encryption
	 * 'E': client-to-server MAC
	 * 'F': server-to-client MAC
	 * 
	 * @param sharedSecret Shared secret number
	 * @param exchangeHash
	 * @param thing The key type requested 'A'-'F'
	 * @param sessionID SSH session ID; the first exchange hash of the session
	 * @return
	 */
	public static byte[] generateKeyWithSHA1(BigInteger sharedSecret, byte[] exchangeHash,
			byte keyType, byte[] sessionID)
	{
		SHA1Digest sha1 = new SHA1Digest();
		byte[] buf = new byte[sha1.getDigestSize()];
		byte[] ssba = sharedSecret.toByteArray();
		sha1.update(intToByteArray(ssba.length), 0, 4);
		sha1.update(ssba, 0, ssba.length);
		sha1.update(exchangeHash, 0, exchangeHash.length);
		sha1.update(keyType);
		sha1.update(sessionID, 0, sessionID.length);
		sha1.doFinal(buf, 0);
		
		return buf;
	}

	/**
	 * Converts an int to an array of bytes, in big-endian format.
	 * 
	 * @param n
	 * @return
	 */
	public static byte[] intToByteArray(int n)
	{
		byte[] data = new byte[4];
		
		data[0] = (byte) ((n >> 24) & 0xff);
		data[1] = (byte) ((n >> 16) & 0xff);
		data[2] = (byte) ((n >> 8) & 0xff);
		data[3] = (byte) (n & 0xff);

		return data;
	}

	/**
	 * Turns this Packet into a KEX_INIT packet.
	 */
	public static void becomeKexInitPacket(SSH2.Packet p)
	{
		p.reset();
		p.putByte((byte) SSH2.MSG_KEXINIT);
		// Cookie, 16 bytes of random data
		p.putInt(random_.nextInt());
		p.putInt(random_.nextInt());
		p.putInt(random_.nextInt());
		p.putInt(random_.nextInt());

		// Tell the other side about the protocols we support
		p.putString("diffie-hellman-group-exchange-sha1"); // Key exchange algorithms
		p.putString("ssh-dss"); // Server key algorithms
		p.putString("aes128-cbc"); // Client-to-server encryption
		p.putString("aes128-cbc"); // Server-to-client
		p.putString("hmac-sha1"); // Client-to-server MAC
		p.putString("hmac-sha1"); // Server-to-client MAC
		p.putString("none"); // Client-to-server compression
		p.putString("none"); // Server-to-client compression
		p.putString(""); // Client-to-server languages
		p.putString(""); // Server-to-client languages
	
		p.putByte((byte) 0); // kex byte
		p.putInt(0); // reserved field
	}
	
}
