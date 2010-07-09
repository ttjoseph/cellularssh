package com.zwerdog.cellularssh;

import net.rim.device.api.ui.*;
import net.rim.device.api.system.Bitmap;
import java.io.IOException;

/**
 * A terminal emulator Field for BlackBerry applications.
 * It supposedly supports a useful set of VT100-style terminal commands.  It uses
 * its own font and does not rely on the system-provided font drawing routines.
 * The font this class uses is included as a resource.
 * 
 * Use write() to send characters to the terminal.  Importantly, this class does not
 * itself do any I/O, whether remotely or with the user pressing keys - you have to handle
 * that yourself, outside this class.
 * 
 * @author Tom Joseph
 *
 */
public class TerminalEmulatorField extends Field implements DrawStyle
{
	private static final byte[] fontData_ =
	{ 0,-96,0,0,0,16,-120,0,0,0,0,64,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
	0,0,0,0,56,-127,3,0,0,16,0,16,-64,1,1,0,8,6,0,0,0,0,0,0,0,0,0,0,32,-124,0,0,0,0,0,0,0,0,0,0,
	0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,96,0,64,96,0,64,0,0,0,0,-128,97,32,0,0,0,4,1,64,80,4,8,
	4,65,1,4,0,8,4,1,8,4,1,0,-108,64,16,20,0,0,2,65,0,8,0,8,4,65,1,4,0,8,4,1,8,4,1,0,-108,64,16,10,
	0,0,2,65,0,4,0,0,0,-95,0,4,96,16,4,1,0,0,0,64,14,-31,56,-56,-57,124,-114,3,0,0,0,56,30,-15,56,-57,-9,57,-111,-61,
	69,65,52,57,-113,-13,56,95,20,69,81,-12,9,1,66,0,4,16,0,16,32,0,1,-127,8,4,0,0,0,0,0,2,0,0,0,0,16,4,1,0,
	0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,16,64,68,16,-118,-121,0,-128,-9,17,0,-126,16,-128,
	15,0,-122,2,68,-111,68,16,-126,-94,40,10,-17,16,-126,-94,16,-126,-94,56,10,33,40,-118,2,0,-124,-96,40,-124,-32,16,-126,-94,40,10,0,16,-126,-94,
	16,-126,-94,88,10,33,40,-123,2,0,-124,-96,40,66,-96,0,0,-95,40,-98,-104,16,2,66,16,0,0,32,-111,17,69,76,32,64,81,4,0,16,16,68,
	33,17,69,73,16,68,17,1,37,65,52,69,81,20,69,68,20,85,81,4,9,2,-94,0,8,16,0,16,32,0,1,0,8,4,0,0,0,0,0,2,
	0,0,0,0,16,4,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,-128,16,97,68,16,64,-56,
	0,64,8,40,4,97,0,-64,5,0,-124,2,36,-55,2,0,0,0,0,-124,18,1,0,0,0,0,0,72,0,0,0,0,16,-79,0,0,0,-111,32,1,
	0,0,0,4,0,0,0,0,0,0,0,32,0,0,0,0,64,-80,0,0,0,64,0,0,0,1,124,69,-107,0,2,82,17,0,0,32,17,1,65,74,
	16,32,81,68,16,-52,103,64,-83,18,5,81,16,4,17,1,21,-63,86,69,81,20,5,68,20,85,74,-124,8,2,18,1,-128,-13,120,-98,-13,56,-113,-63,
	72,-60,-14,56,-113,-41,120,78,20,69,81,-12,17,4,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
	0,-63,121,-98,67,56,64,-85,-112,64,10,16,-124,-128,0,-46,5,0,4,-111,36,-119,66,16,4,65,16,-124,18,124,-33,-9,57,-114,-29,-120,-109,-29,56,-114,
	-93,72,81,20,69,-111,35,57,-114,-29,56,-114,-27,57,-114,-29,24,-122,97,64,-113,-29,56,-114,3,72,81,20,69,-47,19,1,0,1,40,-114,98,0,2,-30,
	124,-64,7,16,21,-63,48,-55,-13,32,-114,7,0,3,-128,33,-83,-14,4,-47,-13,100,31,1,13,-63,86,69,79,-12,56,68,-92,84,-124,67,8,4,2,0,
	0,20,5,81,36,68,17,-127,40,68,21,69,81,52,5,66,20,85,74,-124,8,4,98,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
	0,0,0,0,0,0,0,0,0,-95,16,18,1,68,64,-55,72,94,9,0,-97,99,0,-110,69,0,14,32,-111,116,-119,40,-118,-94,40,-118,23,4,65,16,
	16,4,65,-68,85,20,69,81,68,104,81,20,69,-111,-28,64,16,4,65,16,26,68,81,20,17,4,65,120,81,20,69,81,-12,105,81,20,69,81,20,1,
	0,1,40,20,-107,2,2,82,17,0,0,16,17,33,64,31,20,17,17,4,0,-52,103,16,-67,19,5,81,16,68,17,1,21,65,85,69,65,20,65,68,
	-92,40,10,33,8,4,2,0,-128,23,5,-47,39,68,17,-127,24,68,21,69,81,20,56,66,-92,84,68,68,16,4,-111,1,0,0,0,0,0,0,0,0,
	0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,-95,8,-46,71,68,64,11,36,80,9,0,4,0,0,18,5,0,0,64,-46,36,13,57,
	-114,-29,56,78,18,60,-49,-13,16,4,65,-120,85,20,69,81,-92,88,81,20,69,-114,36,121,-98,-25,121,-98,23,124,-33,-9,17,4,65,68,81,20,69,81,
	4,88,81,20,69,81,20,1,0,0,124,-113,26,1,2,66,16,0,0,8,17,17,68,72,20,17,17,66,16,16,16,0,65,20,69,73,16,68,17,1,
	37,65,-107,69,65,22,69,68,68,40,17,17,8,8,2,0,64,20,5,81,32,68,17,-127,40,68,21,69,81,20,64,66,-92,40,74,36,16,4,1,0,
	0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,-63,5,30,65,56,64,8,72,80,8,0,4,0,0,18,
	5,0,0,32,-23,-110,30,69,81,20,69,81,18,5,65,16,16,4,65,72,89,20,69,81,20,73,81,20,69,-124,35,69,81,20,69,81,18,4,65,16,
	16,4,65,68,81,20,69,81,68,72,81,20,69,81,20,1,0,1,40,68,-28,2,4,1,0,4,64,8,-114,-13,57,-120,-29,8,-114,1,8,0,0,16,
	126,-12,56,-57,23,120,-111,-13,68,95,-108,57,-127,23,57,-124,67,40,17,-15,9,8,2,0,-128,-9,120,-98,39,120,17,-127,72,68,21,57,-113,23,60,-100,
	71,40,-111,-9,17,4,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,-127,124,33,65,16,-128,7,
	-112,-112,7,0,31,0,0,46,5,48,0,-112,-120,-78,-24,68,81,20,69,81,-18,124,-33,-9,57,-114,-29,56,-103,-29,56,-114,3,52,-114,-29,56,-124,-32,120,
	-98,-25,121,-98,-19,121,-98,-25,17,4,65,56,-111,-29,56,-114,3,52,-98,-25,121,-34,-29,1,0,0,0,0,0,0,-120,0,0,2,0,4,0,0,0,0,
	0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,56,-112,3,-4,0,0,0,0,0,64,0,-128,
	0,0,0,0,1,4,0,0,0,0,0,4,32,-124,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
	0,0,0,0,64,16,0,0,0,0,0,0,0,0,0,2,5,32,0,0,4,65,0,0,0,0,0,0,64,0,0,0,0,0,0,0,0,0,0,0,
	0,0,0,0,0,-128,16,0,0,0,0,0,64,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,80,0,1,0,0,0,0,0,0,0,0,
	0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
	0,0,0,0,0,56,0,112,0,0,0,0,1,4,0,0,0,0,-128,3,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
	0,0,0,0,0,0,0,0,0,0,0,0,0,12,0,0,0,0,0,0,0,0,0,1,5,16,0,0,0,0,0,0,0,0,0,0,32,0,0,0,
	0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,32,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,78,-32,0 };
	
	private final Bitmap font_ = new Bitmap(Bitmap.ROWWISE_MONOCHROME, 1338, 10, fontData_);
	public final int FONT_CHAR_HEIGHT = 10, FONT_CHAR_WIDTH = 6;
	private final int DEFAULT_NUM_ROWS = 24, DEFAULT_NUM_COLS = 53;
	private final char ANSI_ESCAPE_CHAR = 0x1b;
	// Lookup table: ANSI color codes to RGB - enables us to use bytes instead of ints
	// for the color buffers
	private final int COLOR_TABLE[] = {
		0x00000000, // Black 
		0x00d00000, // Red
		0x0000d000, // Green
		0x00d0d000, // Yellow
		0x004040d0, // Blue
		0x00d000d0, // Magenta
		0x0000d0d0, // Cyan
		0x00d0d0d0  // White
		};
	private final int
		ANSI_MODE_NORMAL = 0,
		ANSI_MODE_RECEIVED_ESCAPE = 1,
		ANSI_MODE_CAPTURING_ESCAPE_SEQUENCE = 2;
	
	// Position of cursor on screen and dimensions of terminal window
	private int cursorRow_, cursorCol_, savedCursorRow_ = 0, savedCursorCol_ = 0, numRows_, numCols_;
	private int ansiMode_ = ANSI_MODE_NORMAL;
	// Buffer of the actual characters to draw
	private char[] charBuffer_;
	// Use high 4 bits for background color, low 4 bits for foreground color
	private byte[] colorBuffer_;
	private boolean[] tabStops_;
	private byte currentForegroundColor_ = 7, currentBackgroundColor_ = 0;
	private StringBuffer ansiEscapeSequence_;
	private Session session_;
	
	private char debug_lastCharReceived_ = 0;
	
	/**
	 * Initializes a TerminalEmulatorField with default values for number of rows and columns.  
	 */
	public TerminalEmulatorField()
	{
		super();
		init(DEFAULT_NUM_ROWS, DEFAULT_NUM_COLS);
	}
	
	/**
	 * Initializes a TerminalEmulatorField of a specified size, specified in pixels.
	 * The field may end up being a little bit smaller because it will only size itself in whole
	 * character sizes.
	 *
	 * @param maxPixelHeight Maximum height, in pixels
	 * @param maxPixelWidth Maximum width, in pixels
	 * @param sshSession VT100 responses are sent through this
	 */
	public TerminalEmulatorField(int maxPixelHeight, int maxPixelWidth)
	{
		super();
		init(maxPixelHeight / FONT_CHAR_HEIGHT, maxPixelWidth / FONT_CHAR_WIDTH);
	}
	
	/**
	 * Does the actual initialization.  Called from constructors.
	 * 
	 * @param numRows Number of rows of characters to show.
	 * @param numCols Number of columns of characters to show.
	 */
	private void init(int numRows, int numCols)
	{
		numRows_ = numRows;
		numCols_ = numCols;
		charBuffer_ = new char[numRows_ * numCols_];
		colorBuffer_ = new byte[numRows_ * numCols_];
		tabStops_ = new boolean[numCols_];
		setTabStopsToDefault();
		eraseScreen();
	}
	
	/**
	 * Erases part of the screen.
	 * 
	 * @param startOffset Offset within the character buffer (NOT row/column!!)
	 * @param length Number of characters to erase
	 */
	private void erasePartOfScreen(int startOffset, int length)
	{
		for(int i = startOffset; i < (startOffset + length); i++)
		{
			charBuffer_[i] = ' ';
			colorBuffer_[i] = 7; // Regular white
		}
		
		invalidate();
	}
	
	/**
	 * Erases the entire screen.
	 */
	private void eraseScreen()
	{
		erasePartOfScreen(0, numRows_ * numCols_);
	}

	/**
	 * Does layout, I guess?
	 * This is an overloaded method.
	 */
	protected void layout(int width, int height)
	{
		setExtent(getPreferredWidth(), getPreferredHeight());
	}
	
	/**
	 * Redraw the terminal screen.
	 * 
	 * @param graphics The Graphics context to draw on.
	 */
	protected void paint(Graphics graphics)
	{
		// TODO: Blinking cursor

		// Only redraw dirty region
		XYRect dirtyRect = graphics.getClippingRect();
		int startCol = dirtyRect.x / FONT_CHAR_WIDTH;
		int endCol = (dirtyRect.x + dirtyRect.width - 1) / FONT_CHAR_WIDTH;
		int startRow = dirtyRect.y / FONT_CHAR_HEIGHT;
		int endRow = (dirtyRect.y + dirtyRect.height - 1) / FONT_CHAR_HEIGHT;

		for(int row = startRow; row <= endRow; row++)
		{
			for(int col = startCol; col <= endCol; col++)
			{
				int offset = numCols_ * row + col;
				graphics.setBackgroundColor(COLOR_TABLE[(colorBuffer_[offset] & 0xf0) >> 4]);
				graphics.setColor(COLOR_TABLE[colorBuffer_[offset] & 0x0f]);
				drawChar(graphics, charBuffer_[offset], FONT_CHAR_WIDTH * col, FONT_CHAR_HEIGHT * row);
			}
		}

		// Draw the cursor
		graphics.setColor(0x00ffff00);
		graphics.drawRect(FONT_CHAR_WIDTH * cursorCol_, FONT_CHAR_HEIGHT * cursorRow_, FONT_CHAR_WIDTH, FONT_CHAR_HEIGHT);

		// DEBUG: write out ASCII code of last character received
//		graphics.setBackgroundColor(0x00dddddd);
//		graphics.setColor(0x00ffffff);
//		graphics.drawText(new String("" + (int) debug_lastCharReceived_), 0, 0);
	}
	
	/**
	 * Returns width of terminal field in pixels
	 * 
	 * @return Width of terminal in pixels
	 */
	public int getPreferredWidth()
	{
		return numCols_ * FONT_CHAR_WIDTH;
	}
	
	/**
	 * Returns height of terminal field in pixels
	 * 
	 * @return Height of terminal in pixels
	 */
	public int getPreferredHeight()
	{
		return numRows_ * FONT_CHAR_HEIGHT;
	}
		
	/**
	 * Returns the height of the terminal field in characters.
	 * 
	 * @return Number of rows
	 */
	public int getNumRows()
	{
		return numRows_;
	}

	/**
	 * Returns the width of the terminal screen in characters.
	 * 
	 * @return Number of columns
	 */
	public int getNumCols()
	{
		return numCols_;
	}

	/**
	 * Scrolls the character buffer up one line.
	 * 
	 * TODO: Save the line that is scrolled away into a scrollback buffer
	 */
	private void scrollUpOneLine()
	{
		// Move the bottom n - 1 rows up one row
		for(int i = numCols_; i < (numRows_ * numCols_); i++)
		{
			charBuffer_[i - numCols_] = charBuffer_[i];
			colorBuffer_[i - numCols_] = colorBuffer_[i];
		}
		
		// Fill the last row with spaces
		for(int i = 0; i < numCols_; i++)
		{
			charBuffer_[numCols_ * (numRows_ - 1) + i] = ' ';
			// XXX: Magic number!
			colorBuffer_[numCols_ * (numRows_ - 1) + i] = 7;
		}
		
		// We should redraw everything
		invalidate();
	}
	
	/**
	 * Moves the cursor to the next line, scrolling up if necessary.
	 * 
	 */
	private void lineFeed()
	{
		// Last row?
		if(cursorRow_ == (numRows_ - 1))
			scrollUpOneLine();
		else
			cursorRow_++;
	}
	
	/**
	 * Converts strings of the form '2;32;42' to an array of ints.  Will respect the size
	 * of the output array and silently discard any extra input.
	 * 
	 * @param out Output array
	 * @param s String to examine
	 * @return Number of ints extracted
	 */
	public static int getIntListFromAnsiEscapeSequence(int[] out, final String s)
	{
		int out_i = 0, numStart = 0;
		
		for(int i = 0; i < s.length(); i++)
		{
			char c = s.charAt(i);
			// Are we at the end of a number? This can be either a semicolon
			// or the end of the string
			if(c == ';' || i == (s.length() - 1))
			{
				int numEnd = i;
				if(c != ';' && i == s.length() - 1)
					numEnd = i + 1;
				if(numEnd - numStart > 0)
				{
					// We don't care if the parseInt fails...
					// XXX: But we should! Make this more robust!
					try { out[out_i++] = Integer.parseInt(s.substring(numStart, numEnd)); }
					catch(NumberFormatException e) {}
				}
				numStart = i + 1;
				
				// Return if we've run out of room in the output array
				if(out_i >= out.length)
					return out_i;
			}			
		}
		return out_i;
	}
	
	/**
	 * Clears all tab stops.
	 */
	private void clearAllTabStops()
	{
		for(int i = 0; i < numCols_; i++)
			tabStops_[i] = false;
	}
	
	/**
	 * Sets tab stops to default of every 8 columns.
	 */
	private void setTabStopsToDefault()
	{
		clearAllTabStops();
		for(int i = 7; i < numCols_; i += 8)
			tabStops_[i] = true;
	}
	
	/**
	 * Clip cursor position to the edge of the screen.
	 */
	private void boundsCheckCursorPosition()
	{
		if(cursorRow_ < 0)
			cursorRow_ = 0;
		else if(cursorRow_ >= numRows_)
			cursorRow_ = numRows_ - 1;
		if(cursorCol_ < 0)
			cursorCol_ = 0;
		else if(cursorCol_ >= numCols_)
			cursorCol_ = numCols_ - 1;
	}
	
	private void invalidateCharacterPosition(int row, int col)
	{
		int x = col * FONT_CHAR_WIDTH;
		int y = row * FONT_CHAR_HEIGHT;
		
		invalidate(x, y, FONT_CHAR_WIDTH, FONT_CHAR_HEIGHT);
	}
	
	/** 
	 * Writes a character to the terminal.  It will actually be drawn at the next repaint.
	 * This routine will move the cursor to the next position and scroll if necessary.
	 * Also, it handles ANSI escape sequences.
	 * 
	 * TODO: Break this up into smaller functions? This is gigantic.
	 * 
	 * @param c Character to write to the terminal.
	 */
	private void write(char c)
	{
		debug_lastCharReceived_ = c;
		
		// Handle ANSI escape sequence
		if(ansiMode_ == ANSI_MODE_RECEIVED_ESCAPE)
		{
			switch(c)
			{
			case '[':
				// At this point we've already received an escape character
				// Now we must absorb rest of sequence
				ansiMode_ = ANSI_MODE_CAPTURING_ESCAPE_SEQUENCE;
				return;
			case '7': // Save cursor position
				// We don't do bounds checking here because presumably we'd done it before
				savedCursorRow_ = cursorRow_;
				savedCursorCol_ = cursorCol_;
				break;
			case '8': // Restore cursor position
				cursorRow_ = savedCursorRow_;
				cursorCol_ =  savedCursorCol_;
				break;
			case 'H': // Set tab stop at current column
				tabStops_[cursorCol_] = true;
				break;
			}
			
			// At this point the escape sequence is over
			ansiMode_ = ANSI_MODE_NORMAL;
			return;
		}
		else if(ansiMode_ == ANSI_MODE_CAPTURING_ESCAPE_SEQUENCE)
		{
			// Keep the character if it isn't a command suffix
			if(Character.isDigit(c) || c == ';')
				ansiEscapeSequence_.append(c);
			else
			{
				//System.err.println("TEF: ANSI escape sequence: " + ansiEscapeSequence_);
				
				int offset;
				int[] numList = new int[5];
				int length = getIntListFromAnsiEscapeSequence(numList, ansiEscapeSequence_.toString());
				// The character was a command suffix
				switch(c)
				{
				case 'm': // Change colors
					// Go from left to right, extracting colors and setting them
					for(int i = 0; i < length; i++)
					{
						if(numList[i] >= 30 && numList[i] <= 37)
							currentForegroundColor_ = (byte) (numList[i] - 30);
						else if(numList[i] >= 40 && numList[i] <= 47)
							currentBackgroundColor_ = (byte) (numList[i] - 40);
						else if(numList[i] == 0)
						{
							currentForegroundColor_ = 7;
							currentBackgroundColor_ = 0;
						}
					}
					break;
				
				case 'H': // Move cursor to specific position
				case 'f':
					if(length != 2)
					{
						// If no coordinates were provided, move cursor to upper-left
						if(length == 0)
							cursorRow_ = cursorCol_ = 0;						
						break;
					}
					cursorRow_ = numList[0];
					cursorCol_ = numList[1];
					boundsCheckCursorPosition();
					break;
				
				case 'A': // Move up
					cursorRow_ = (length >= 1) ? cursorRow_ - numList[0] : cursorRow_ - 1;
					boundsCheckCursorPosition();
					break;
				
				case 'B': // Move down
					cursorRow_ = (length >= 1) ? cursorRow_ + numList[0] : cursorRow_ + 1;
					boundsCheckCursorPosition();
					break;
					
				case 'C': // Move forward
					cursorCol_ = (length >= 1) ? cursorCol_ + numList[0] : cursorCol_ + 1;
					boundsCheckCursorPosition();
					break;
					
				case 'D': // Move backward
					cursorCol_ = (length >= 1) ? cursorCol_ - numList[0] : cursorCol_ - 1;
					boundsCheckCursorPosition();
					break;
					
				case 'g': // Clear tab stop at current column
					if(length == 0)
						tabStops_[cursorCol_] = false;
					else if(numList[0] == 3)
						clearAllTabStops();
					break;
					
				case 'K': // Erase current line or some part of it
					if(length == 0)
					{
						offset = numCols_ * cursorRow_ + cursorCol_;
						// Erase to end of line
						for(int i = cursorCol_; i < numCols_; i++)
						{
							charBuffer_[offset] = ' ';
							colorBuffer_[offset] = 7; // Regular white
							offset++;
						}
					} else if(numList[0] == 1) // Erase to start of line
					{
						offset = numCols_ * cursorRow_;
						erasePartOfScreen(offset, cursorCol_);
					} else if(numList[0] == 2) // Erase entire line
					{
						offset = numCols_ * cursorRow_;
						erasePartOfScreen(offset, numCols_);
					}
					break;
				
				case 'J': // Erase multiple lines
					if(length == 0) // Erase current line all the way to bottom of screen
					{
						offset = numCols_ * cursorRow_;
						erasePartOfScreen(offset, numCols_ * numRows_ - offset);
					} else if(numList[0] == 1) // TODO: implement rest of J commands
					{
						// Erase all of this line up to top of screen
						offset = numCols_ * cursorRow_ + numCols_ - 1;
						erasePartOfScreen(0, offset);
					} else if(numList[0] == 2) // Erase whole screen
					{
						eraseScreen();
					}
					break;
					
				case 'S': // Scroll up
					if(length == 0)
						scrollUpOneLine();
					else // Scroll up some number of lines
					{
						// TODO: This is friggin slow
						for(int i = 0; i < numList[0]; i++)
							scrollUpOneLine();
					}
					break;
					
				case 'n': // Various queries
					if(length > 1)
					{
						switch(numList[0])
						{
						case 6: // Query cursor position
							// Make sure the caller had given us an SshSession
							// We may not have one in the case of terminals used strictly to look good and do nothing
							/*
							if(sshSession_ != null)
							{
								try {
									sshSession_.handleSendData((((byte) ANSI_ESCAPE_CHAR) + "[" + cursorRow_ + ";" 
											+ cursorCol_ + "R").getBytes());
								} catch (IOException e) {}
							}*/
							break;
						}
					}
					break;
					
				case 's': // Save cursor position
					savedCursorRow_ = cursorRow_;
					savedCursorCol_ = cursorCol_;
					break;
					
				case 'u': // Restore ("unsave") cursor position
					cursorRow_ = savedCursorRow_;
					cursorCol_ = savedCursorCol_;
					break;
					
				case 'c': // Device attribute query
					/*
					if(sshSession_ != null)
					{
						try {
							sshSession_.handleSendData((((byte) ANSI_ESCAPE_CHAR) + "[?1;0c").getBytes());
						} catch (IOException e) {}
					}*/
					break;
					
				}

				// Clear out this escape sequence
				ansiEscapeSequence_ = new StringBuffer();
				ansiMode_ = ANSI_MODE_NORMAL;
			}
			
			// We don't want to display this character since it was part of an
			// ANSI escape sequence
			return;
		}
		
		if(c == '\n') // Handle newline
		{
			cursorCol_ = 0;
			lineFeed();
		}
		else if(c == '\r') // Handle line feed
			cursorCol_ = 0;
		else if(c == ANSI_ESCAPE_CHAR)
		{
			ansiMode_ = ANSI_MODE_RECEIVED_ESCAPE;
			ansiEscapeSequence_ = new StringBuffer();
		} else if(((byte)c) == 24) // ANSI cancel escape sequence
		{
			ansiMode_ = ANSI_MODE_NORMAL;
			ansiEscapeSequence_ = new StringBuffer();
		}
		else if(((byte) c) == 7) // ASCII Bell
		{
			// Um, beeps are annoying, so we'll ignore it.
		}
		else if(((byte) c) == 8) // ASCII Backspace
		{
			cursorCol_--;
			if(cursorCol_ < 0)
				cursorCol_ = 0;
		} else if(((byte) c) == 11) // Tab
		{
			// Go to next tab stop
			for(int i = cursorCol_ + 1; i < numCols_; i++)
			{
				if(tabStops_[i] == true)
				{
					cursorCol_ = i;
					break;
				}
			}
		}
		else // Some other character, so draw it
		{
			int offset = cursorRow_ * numCols_ + cursorCol_;
			charBuffer_[offset] = c;
			// High 4 bits are background color, low 4 bits are foreground color
			colorBuffer_[offset] = currentForegroundColor_;
			colorBuffer_[offset] |= currentBackgroundColor_ << 4;
			
			cursorCol_++;
			cursorCol_ = cursorCol_ % numCols_;
			
			// If we've wrapped around, line feed
			if(cursorCol_ == 0)
				lineFeed();
		}

		// Don't want to redraw each character at a time
		//invalidateCharacterPosition(cursorRow_, cursorCol_);
	}
	
	/**
	 * Writes an array of bytes to the terminal.
	 * 
	 * @param buf Buffer of bytes to write
	 * @param offset Offset within buffer to start at
	 * @param length Number of bytes to write
	 */
	public void write(byte[] buf, int offset, int length)
	{
		int max_i = length + offset;
		for(int i = offset; i < max_i; i++)
			write((char) buf[i]);
		
		invalidate();
	}
	
	/**
	 * Prints a string on the terminal.
	 * 
	 * @param s The string to print.
	 */
	public void write(String s)
	{
		for(int i = 0; i < s.length(); i++)
			write(s.charAt(i));
		
		invalidate();
	}

	/**
	 * Draws a character from the bitmap font at the specified pixel location.
	 * 
	 * @param g Graphics context to draw on.
	 * @param c Character to draw (ASCII).
	 * @param x X coordinate to draw at.
	 * @param y Y coordinate to draw at.
	 */
	private void drawChar(Graphics g, char c, int x, int y)
	{
		// 'A' starts at offset 32*Width
		int left = FONT_CHAR_WIDTH * (33 + (c - 'A'));
		int top = 0;

		// Don't blit from a nonexistent part of the font bitmap
		//if(top < 0 || top > (font_.getHeight() - FONT_CHAR_HEIGHT))
		//	top = 0;
		if(left < 0 || left > (font_.getWidth() - FONT_CHAR_WIDTH))
			left = 0;
		
		// Why RIM called this method "rop" instead something more descriptive like "doRasterOperation"
		// completely escapes me
		g.rop(Graphics.ROP_SRCMONOEXPAND_COPY, x, y, FONT_CHAR_WIDTH, FONT_CHAR_HEIGHT, font_, left, top);
	}

	/**
	 * Draws a string with characters from the bitmap font at the specified location.
	 * 
	 * @param g Graphics context to draw on.
	 * @param s String to draw.
	 * @param x X coordinate to draw at.
	 * @param y Y coordinate to draw at.
	 */
	private void drawString(Graphics g, String s, int x, int y)
	{
		for(int i = 0; i < s.length(); ++i)
			drawChar(g, s.charAt(i), x + (i * FONT_CHAR_WIDTH), y);
	}

	public void setSession(Session session)
	{
		session_ = session;		
	}
}
