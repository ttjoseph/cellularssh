package com.zwerdog.cellularssh;
/**
 * A FIFO queue that only holds bytes.
 * 
 * @author Tom Joseph
 */

public class ByteQueue
{
	private int head_, tail_;
	private byte[] data_ = null;

	public ByteQueue(int size)
	{
		head_ = tail_ = 0;
		data_ = new byte[size];
	}
	
	/**
	 * Returns the number of elements in the queue.  This is calculated each time,
	 * not cached, not that you care.
	 * 
	 * @return Number of elements in the queue.
	 */
	public synchronized int available()
	{
		int bytesInQueue;
		if(tail_ >= head_)
			bytesInQueue = tail_ - head_;
		else
			bytesInQueue = data_.length - (head_ - tail_);
		
		return bytesInQueue;
	}
	
	public void resize(int newLength)
	{
		if(newLength < data_.length)
			return;
		
		byte[] newData = new byte[newLength];
		int bytesInQueue = available();
		int ptr = head_;
		
		for(int i = 0; i < bytesInQueue; i++)
		{
			newData[i] = data_[ptr % data_.length];
			ptr++;
		}
		
		head_ = 0;
		tail_ = ptr; // tail_ = bytesInQueue
		data_ = newData;
	}
	
	/**
	 * Add bytes to the queue.
	 * 
	 * @param bytes Array of bytes to copy bytes from.
	 * @param offset Offset within the caller's array to start copying bytes from.
	 * @param length Number of bytes to copy.
	 * 
	 * @return True on success.  Returns false if the queue isn't big enough.
	 */
	public synchronized boolean write(byte[] bytes, int offset, int length)
	{
		// Add data to the queue.  It's tacked on the end, so tail_ is advanced.
		// Invariant: tail_ points to the byte just after the end of valid data in the queue.
		// Invariant: head_ points to the oldest byte in the queue.
		// Invariant: There are only bytes on the queue when tail_ != head_.
		
		// If we're given too much data, make our buffer big enough to hold it
		// and then some, so we won't have to resize again if next time we need just a 
		// couple bytes more
		if(length > (data_.length - available() - 1))
			resize(available() + length + 256);
		
		for(int i = offset; i < length + offset; i++)
		{
			data_[tail_++] = bytes[i];
			tail_ = tail_ % data_.length;
		}
		
		return true;
	}
	
	/**
	 * Puts some bytes on the queue.
	 * 
	 * @param bytes
	 * @return True on success
	 */
	public synchronized boolean write(byte[] bytes)
	{
		return write(bytes, 0, bytes.length);
	}
	
	/**
	 * Puts one byte on the queue.
	 * @param b The byte you want on the queue.
	 * @return True on success
	 */
	public synchronized boolean write(byte b)
	{
		byte[] tmp = new byte[1];
		tmp[0] = b;
		return write(tmp);
	}
	
	/**
	 * Puts one char on the queue
	 * @param c The char to go on the queue
	 * @return true on success
	 */
	public synchronized boolean write(char c)
	{
		byte[] tmp = new byte[1];
		tmp[0] = (byte) c;
		return write(tmp);
	}
	
	/**
	 * Puts a string on the queue.
	 * 
	 * @param s
	 * @return True on success.s
	 */
	public synchronized boolean write(String s)
	{
		// XXX: Dependent on character encoding!
		// As of JDE 4.2.1, the default is ISO-8859-1
		return write(s.getBytes());
	}
	
	/**
	 * Pops a specified number of bytes from the queue, or fewer if there aren't enough bytes
	 * on the queue.
	 * Returns number of bytes actually popped, which could be less than the caller asked for.
	 * This is so the caller doesn't have to keep asking how many bytes we have.
	 * 
	 * @param out
	 * @param length
	 * @return Number of bytes actually popped.
	 */
	public int read(byte[] b, int off, int len)
	{
		int doableLength = len;
		
		if(doableLength > available())
			doableLength = available();
		
		// Advance head_ for each byte popped.
		// If we reach the end of the data_ array, wrap around
		int outIndex = 0;
		for(int i = 0; i < doableLength; i++)
		{
			b[off + outIndex++] = data_[head_++];
			head_ = head_ % data_.length;
		}
		
		return doableLength;
	}
	
}
