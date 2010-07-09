package com.zwerdog.cellularssh;

import net.rim.blackberry.api.menuitem.*;
import net.rim.device.api.ui.*;
import net.rim.device.api.io.*;
import net.rim.device.api.ui.container.*;
import net.rim.device.api.ui.component.*;
import net.rim.device.api.system.*;

import javax.microedition.io.*;
import java.io.*;

public class SessionScreen extends MainScreen implements Session.StatusListener
{
	/**
	 * Shows the SSH connection initiation status in a popup screen, with progress bar and messages.
	 * 
	 * @author T. Joseph <ttjoseph@gmail.com>
	 */
	public class StatusPopupScreen extends PopupScreen
	{
		private RichTextField statusMessage_;
		private ButtonField dismissButton_;
		private SessionScreen sessionScreen_;
		private GaugeField gauge_;
		
		public StatusPopupScreen(SessionScreen sessionScreen)
		{
			super(new VerticalFieldManager());
			sessionScreen_ = sessionScreen;
			statusMessage_ = new RichTextField("Initializing...", Field.NON_FOCUSABLE | Field.FIELD_VCENTER);
			statusMessage_.setPadding(5, 5, 5, 5);
			statusMessage_.setEditable(false);
			dismissButton_ = new ButtonField("Cancel", Field.FIELD_HCENTER);
			dismissButton_.setChangeListener(new FieldChangeListener()
			{
				public void fieldChanged(Field field, int context)
				{
					UiApplication.getUiApplication().popScreen(StatusPopupScreen.this);
					UiApplication.getUiApplication().popScreen(sessionScreen_);
				}
				
			});
			gauge_ = new GaugeField(null, 0, 5, 0, GaugeField.LABEL_AS_PROGRESS);
			gauge_.setPadding(5, 5, 5, 5);
			add(gauge_);
			add(statusMessage_);
			add(dismissButton_);
			dismissButton_.setFocus();
		}
		
		public synchronized void close()
		{
			// Only close this screen if it's actually on the display stack
			// Otherwise we get a weird exception from the BlackBerry JVM
			if(isDisplayed())
			{
				UiApplication.getUiApplication().invokeLater(
						new Runnable() { public void run() { UiApplication.getUiApplication().popScreen(StatusPopupScreen.this); } });
			}
		}
		
		/**
		 * Sets the status text.
		 * This is a UI method, so it should be called using invokeLater() or some such.
		 * 
		 * @param message The new text
		 */
		public void setStatusMessage(String message, int stepNumber)
		{
			statusMessage_.setText(message);
			gauge_.setValue(stepNumber);
			//gauge_.setLabel(stepNumber + " of " + gauge_.getValueMax());
		}
		
		public void setButtonText(String s)
		{
			dismissButton_.setLabel(s);
		}
		
//		public boolean keyChar(char key, int status, int time)
//		{
//			if(key == Characters.ESCAPE)
//			{
//				// TODO: tell the SessionScreen to close, too
//				close();
//			}
//			return false;
//		}
//		
//		public boolean keyDown(int keycode, int time) { return false; }
//		public boolean keyRepeat(int keycode, int time) { return false; }
//		public boolean keyStatus(int keycode, int time) { return false; }
//		public boolean keyUp(int keycode, int time) { return false; }
	}
	
	public class TypingPopupScreen extends PopupScreen
	{
		private EditField inputField_;
		private SessionScreen sessionScreen_;
		
		public TypingPopupScreen(SessionScreen sessionScreen)
		{
			super(new HorizontalFieldManager());
			sessionScreen_ = sessionScreen;
			
			// Popup to allow typing of a bunch of characters at a time
			// Has the desirable side-effect of allowing symbol input
			inputField_ = new EditField(Field.USE_ALL_WIDTH | Field.EDITABLE)
			{
				// When user hits Enter, send whatever's in the field
				// Or, if user hits Escape, cancel
				protected boolean keyChar(char key, int status, int time)
				{
					if(key == Characters.ENTER) // User wants to send this out over the network
					{
						session_.send(inputField_.getText().getBytes());
						close();
						return false;
					} else if(key == Characters.ESCAPE) // Allow user to abort input
					{
						close();
						return false;
					}
					
					else
						return super.keyChar(key, status, time);
				}
			};
			add(inputField_);
			inputField_.setFocus();
		}

	}

	public class TheKeyListener implements KeyListener
	{
		byte[] tmp = new byte[1];
		
		public boolean keyDown(int keycode, int time) { return false; }
		public boolean keyRepeat(int keycode, int time) { return false; }
		public boolean keyStatus(int keycode, int time) { return false; }
		public boolean keyUp(int keycode, int time) { return false; }
		public boolean keyChar(char c, int status, int time)
		{
			tmp[0] = (byte) c;

			// Use SYM key for Tab since Tab apparently doesn't exist on BlackBerry
			if(c == Characters.CONTROL_SYMBOL)
				tmp[0] = (byte) '\t';
			
			// Send the character to the remote host
			session_.send(tmp);
			
			// Only allow popping back to the previous screen with Esc if we're not connected
			if(c == Characters.ESCAPE && connected_ == true)
				return true;
			else
				return false;
		}
	}
	
	private StatusPopupScreen statusPopupScreen_;
	private TerminalEmulatorField terminal_;
	private Session session_;

	private int progressStepNumber_ = 0;
	private boolean connected_ = false;
	

	public SessionScreen(Session session)
	{
		super(Screen.DEFAULT_CLOSE);
		session_ = session;
		session_.setListener(this);
		terminal_ = new TerminalEmulatorField(Display.getHeight(), Display.getWidth());
		add(terminal_);
		
		// I think capitalizing menu items is ugly but that's what RIM does,
		// and as such so will I
		this.addMenuItem(new MenuItem("Ctrl Key", 2, 0x20001)
		{

			public void run() 
			{
				// TODO Auto-generated method stub
				// Display the Ctrl state image in the upper-right corner of the screen
				// The next alphanumeric keypress will be interpreted as Ctrl+whatever
				// Esc will cancel Ctrl state
				
			}
			
		});
		
		this.addMenuItem(new MenuItem("Scrollback", 1, 0x20000)
		{
			public void run()
			{
				// TODO Implement scrollback buffer.
				// Probably let's have it be a separate field, perhaps subclassed
				// from TerminalEmulatorField and displayed on a separate Screen,
				// separate from the SSH session(s), so they can continue to update
				// in the background.
			}
			
		});
		
		this.addMenuItem(new MenuItem("Switch Session", 0, 100)
		{
			public void run()
			{
				// TODO Make a popup screen that lists active sessions
			}
		});
		
		addKeyListener(new TheKeyListener());
		
		terminal_.setSession(session_);
		session_.connect();
		
		statusPopupScreen_ = new StatusPopupScreen(this);
	}
	
	/**
	 * Shows the status popup immediately when we're displayed.
	 */
	protected void onDisplay()
	{
		UiApplication.getUiApplication().pushScreen(statusPopupScreen_);
	}
	
	/**
	 * Overloaded method of Screen used to suppress save dialog.
	 */
	public boolean onSavePrompt()
	{
		return true;
	}
	
	/**
	 * Disconnect upon closing this screen.
	 */
	public boolean onClose()
	{
		// Spawn a worker thread to do the disconnect as it's a blocking operation
		// and we can't do those when handling UI events such as this
		// How's this for a one-liner?
		new Thread() { public void run() { session_.disconnect(); } }.start();
		return super.onClose();
	}
	
	/**
	 * Overloaded Screen method mapping navigation trackball to arrow keys.
	 * Construct ANSI code and sends it.
	 * 
	 * @param dx Horizontal movement, positive for right
	 * @param dy Vertical movement, positive for down
	 * @param status
	 * @param time 
	 */
	protected boolean navigationMovement(int dx, int dy, int status, int time)
	{
		byte b[] = new byte[3];
		
		if(dy < 0) // move up
			b[2] = (byte)'A';
		else if(dy > 0) // move down
			b[2] = (byte)'B';
		else if(dx > 0) // right move
			b[2] = (byte)'C';
		else if(dx < 0) // left move
			b[2] = (byte)'D';
		else
			return false; // Did nothing so we won't consume this event

		// ANSI control characters
		b[0] = 0x1b;
		b[1] = (byte) '[';
		session_.send(b); 
		return true; // We consumed this event
	}
	
	protected boolean navigationClick(int status, int time)
	{
		UiApplication.getUiApplication().pushScreen(new TypingPopupScreen(this));
		return true; // We consumed this event...
	}

	/**
	 * Update the status popup.
	 * 
	 * @param text Text message to show.
	 * @param statusCode A status code that determines the picture to show.
	 */
	public void updateStatus(final int statusCode, final String message)
	{
		// We use invokeLater() when we want to do stuff to the UI
		UiApplication.getUiApplication().invokeLater(new Runnable()
		{
			String theMessage = null;
			public void run()
			{
				// Update progress bar
				switch(statusCode)
				{
				case SSH2.MSG_KEXINIT:
					progressStepNumber_ = 1;
					theMessage = "Exchanging keys...";
					break;
				case SSH2.MSG_KEXDH_GEX_REPLY:
					progressStepNumber_ = 2;
					theMessage = "Continuing key exchange...";
					break;
				case SSH2.MSG_NEWKEYS:
					progressStepNumber_ = 3;
					theMessage = "New keys established. Authenticating...";
					break;
				case SSH2.MSG_USERAUTH_SUCCESS:
					progressStepNumber_ = 4;
					theMessage = "You have been authenticated.";
					statusPopupScreen_.close();
					break;
				case SSH2.MSG_CHANNEL_OPEN_CONFIRMATION:
					progressStepNumber_ = 5;
					theMessage = "Channel open.";
					break;
				case SSH2.MSG_USERAUTH_FAILURE:
					if(SessionScreen.this.isDisplayed())
						close(); // Close the SessionScreen
					// Fall through - only close the SessionScreen on user auth failure
					// This is so the user can take one last look at what's on the terminal
					// If it was a user auth failure, there was nothing on the terminal to begin with
				case SSH2.MSG_DISCONNECT:
					statusPopupScreen_.close();
					Dialog.alert("You were disconnected. Hope this isn't too inconvenient."); // Tell the user about this
					break;
				}

				// Only update the message if it isn't null, because we are invoked on every packet
				// and we only care about certain messages
				if(theMessage != null)
					statusPopupScreen_.setStatusMessage(theMessage, progressStepNumber_);
				
				switch(statusCode)
				{
				// Close status popup once the channel is open (and remote shell is open)
				case SSH2.MSG_CHANNEL_OPEN_CONFIRMATION:
					/** TODO: run GNU screen remotely? */
					statusPopupScreen_.close();
					break;
				}
			}
		});
	}
	
	private void notifyUserOfClosedConnection()
	{
		// We don't want to use Dialog.alert() because modal dialogs are annoying
		terminal_.write(((char) 0x1b) + "[0m\n[Connection closed.]");
	}

	public void onSessionEvent(int type, String message, Object data, int parameter)
	{
		switch(type)
		{
		case Session.StatusListener.RECEIVED_DATA:
			terminal_.write((byte[]) data, 0, parameter);
			break;
			
		case Session.StatusListener.SSH_MESSAGE:
			System.err.println("SessionScreen.onSessionEvent: I got an SSH_MESSAGE: " + parameter);
			if(parameter == SSH2.MSG_CHANNEL_OPEN_CONFIRMATION)
				connected_ = true;
			else if(parameter == SSH2.MSG_DISCONNECT || parameter == SSH2.MSG_CHANNEL_CLOSE)
			{
				notifyUserOfClosedConnection();
				connected_ = false;
			}
			
			// Only update the status popup screen if we're not finished connecting
			if(connected_ == false)
				updateStatus(parameter, (String) data);
			break;
		
		case Session.StatusListener.CONNECTION_FAILURE:
			// I guess it might not hurt to do this regardless of the value of connected_?
			if(connected_ == true)
			{
				notifyUserOfClosedConnection();
				connected_ = false;
			}
			session_.disconnect();
			break;
		}
	}
}
