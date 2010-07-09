package com.zwerdog.cellularssh;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import javax.microedition.io.Connector;
import javax.microedition.io.SocketConnection;

/**
 * The Session class encapsulates a session with a remote host.
 * It contains an SSH2Connection as well as the authentication information.
 * SessionScreen and TerminalEmulatorField are UI elements and SSH2Connection
 * is purely a network abstraction, so this does whatever is needed to make
 * these things work together.
 *
 * This is a BlackBerry-specific class.
 * 
 * @author T. Joseph <ttjoseph@gmail.com>
 */
public class Session implements SSH2Connection.StatusListener
{
	public interface StatusListener
	{
		public static final int
			CONNECTED = 1,
			DISCONNECTED = 2,
			CONNECTION_FAILURE = 3,
			COULD_NOT_SEND = 4,
			RECEIVED_DATA = 5,
			SSH_MESSAGE = 6;	
		
		public void onSessionEvent(int type, String message, Object data, int parameter);
	}
	
	private class WorkerThread implements Runnable
	{
		public void run()
		{
			// Do connection magic
			try
			{
				SocketConnection remoteHost = (SocketConnection) Connector.open("socket://" + host_ + ":" + port_ + ";deviceside=true");
				connection_.open(remoteHost);
				remoteIn_ = new DataInputStream(connection_.openInputStream());
				remoteOut_ = new DataOutputStream(connection_.openOutputStream());
			} catch (IOException e)
			{
				listener_.onSessionEvent(StatusListener.CONNECTION_FAILURE,
						"Couldn't connect to remote host. TODO: This message sucks", null, 0);
				return;
			}

			byte[] buf = new byte[512];
			
			try
			{
				while(true)
				{
					int len = remoteIn_.read(buf, 0, buf.length);
					if(len > 0)
						listener_.onSessionEvent(StatusListener.RECEIVED_DATA, null, buf, len);
					Thread.yield();
				}
			} catch (IOException e)
			{
				listener_.onSessionEvent(StatusListener.CONNECTION_FAILURE, "Disconnected.", null, 0);
			}
		}
	}
	
	private StatusListener listener_;
	private SSH2Connection connection_;
	private DataInputStream remoteIn_;
	private DataOutputStream remoteOut_;
	
	private String host_, username_, password_;
	private int port_;
	
	Session(String host, int port, String username, String password)
	{
		host_ = host;
		port_ = port;
		username_ = username;
		password_ = password;
		connection_ = new SSH2Connection(host_, port_, username_, password_);
		connection_.setListener(this);
		
		// Do-nothing listener
		setListener(new StatusListener() 
			{ public void onSessionEvent(int type, String message, Object data, int parameter) {}} );
	}
	
	public void setListener(StatusListener listener)
	{
		listener_ = listener;
	}
	
	/**
	 * We need to know about the terminal, so we can tell it to do stuff.
	 * @param terminal
	 */
	public void setTerminal(TerminalEmulatorField terminal)
	{
		connection_.setTerminalDimensions(terminal.getNumCols(), terminal.getNumRows(), 
				terminal.FONT_CHAR_WIDTH, terminal.FONT_CHAR_HEIGHT);
	}
	
	public void connect()
	{
		new Thread(new WorkerThread()).start();
	}

	/**
	 * Sends some bytes out to the remote host. This may or may not fail,
	 * but if it does, the caller's StatusListener will be notified.
	 * This way, the caller only has to handle errors in one place.
	 * If we passed along the IOException, the caller would have to deal with
	 * them in every place that send() is called, which is a hassle.
	 * 
	 * @param bytes Data to send.
	 */
	public void send(byte[] bytes)
	{
		// TODO: So, we should put those bytes in a queue for a worker thread to send, because
		// everything downstream blocks on send.
		// So, the worker thread does the touchy business of sending and receiving and updating
		// the screen buffer. The UI thread redraws the screen when the buffer is dirty.
		try {
			remoteOut_.write(bytes);
		} catch (IOException e)
		{
			listener_.onSessionEvent(StatusListener.COULD_NOT_SEND,
					"There was an error sending data.", null, 0);
		}
		
	}

	/**
	 * Disconnects from the remote host.
	 */
	public void disconnect()
	{
		try {
			connection_.close();
		} catch (IOException e) {} // How can closing a connection fail?
		
	}
	
	public void onSSH2ConnectionEvent(int eventType, String message)
	{
		// Pass along SSH messages to the SessionScreen
		listener_.onSessionEvent(StatusListener.SSH_MESSAGE, message, null, eventType);
	}
	
}
