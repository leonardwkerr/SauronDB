package simpledb.log;

import simpledb.buffer.Buffer;
import simpledb.buffer.BufferMgr;
import simpledb.file.Block;
import simpledb.file.FileMgr;
import simpledb.server.SimpleDB;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import static simpledb.file.Page.*;

// DUSTIN EXTRA IMPORT

/**
 * The low-level log manager.
 * This log manager is responsible for writing log records
 * into a log file.
 * A log record can be any sequence of integer and string values.
 * The log manager does not understand the meaning of these
 * values, which are written and read by the
 * {@link simpledb.tx.recovery.RecoveryMgr recovery manager}.
 *
 * @author Edward Sciore
 */
public class LogMgr implements Iterable<BasicLogRecord> {
   /**
    * The location where the pointer to the last integer in the page is.
    * A value of 0 means that the pointer is the first value in the page.
    */
   public static final int LAST_POS = 0;

   private String logfile;
   // TODO: Make this a buffer buf
//   private Page mypage = new Page();
   private Buffer mybuf;
   private BufferMgr bufferMgr = SimpleDB.bufferMgr();

   // TODO: Make a Log Formatter
   private LogFormatter fmtr = new LogFormatter();

   private Block currentblk;
   private int currentpos;


   /**
    * Creates the manager for the specified log file.
    * If the log file does not yet exist, it is created
    * with an empty first block.
    * This constructor depends on a {@link FileMgr} object
    * that it gets from the method
    * {@link simpledb.server.SimpleDB#fileMgr()}.
    * That object is created during system initialization.
    * Thus this constructor cannot be called until
    * {@link simpledb.server.SimpleDB#initFileMgr(String)}
    * is called first.
    *
    * @param logfile the name of the log file
    */
   public LogMgr(String logfile) {
      this.logfile = logfile;
      int logsize = SimpleDB.fileMgr().size(logfile);
      if (logsize == 0) {
         appendNewBlock();
      } else {
         currentblk = new Block(logfile, logsize - 1);
         // TODO: buf.pin(currentblk)
//         mypage.read(currentblk);
//         printLogPageBuffer();
         mybuf = bufferMgr.pin(currentblk);
//         printLogPageBuffer();
         currentpos = getLastRecordPosition() + INT_SIZE;
      }
   }

   /**
    * Ensures that the log records corresponding to the
    * specified LSN has been written to disk.
    * All earlier log records will also be written to disk.
    *
    * @param lsn the LSN of a log record
    */
   public void flush(int lsn) {
      printLogPageBuffer();
      if (lsn >= currentLSN())
         flush();
      printLogPageBuffer();
   }

   /**
    * Returns an iterator for the log records,
    * which will be returned in reverse order starting with the most recent.
    *
    * @see java.lang.Iterable#iterator()
    */
   public synchronized Iterator<BasicLogRecord> iterator() {
      flush();
      return new LogIterator(currentblk);
   }

   /**
    * Appends a log record to the file.
    * The record contains an arbitrary array of strings and integers.
    * The method also writes an integer to the end of each log record whose value
    * is the offset of the corresponding integer for the previous log record.
    * These integers allow log records to be read in reverse order.
    *
    * @param rec the list of values
    * @return the LSN of the final value
    */
   public synchronized int append(Object[] rec) {
      int recsize = INT_SIZE;  // 4 bytes for the integer that points to the previous log record
      for (Object obj : rec)
         recsize += size(obj);
      if (currentpos + recsize >= BLOCK_SIZE) { // the log record doesn't fit,
         // TODO: flush() will not be necessary
//         flush();        // so move to the next block.
         appendNewBlock();
      }
      for (Object obj : rec) {
         appendVal(obj);
      }
      finalizeRecord();
      return currentLSN();
   }

   /**
    * Adds the specified value to the page at the position denoted by
    * currentpos.  Then increments currentpos by the size of the value.
    *
    * @param val the integer or string to be added to the page
    */
   private void appendVal(Object val) {
      if (val instanceof String) {
         // TODO: buf.setString(currentpos, (String) val, currentLSN(), -1)
//         mypage.setString(currentpos, (String) val);
         mybuf.setString(currentpos, (String) val, currentLSN(), -1);
      } else {
         // TODO: buf.setInt(currentpos, (Integer) val, currentLSN(), -1)
//         mypage.setInt(currentpos, (Integer) val);
         mybuf.setInt(currentpos, (Integer) val, currentLSN(), -1);
      }
      currentpos += size(val);
   }

   /**
    * Calculates the size of the specified integer or string.
    *
    * @param val the value
    * @return the size of the value, in bytes
    */
   private int size(Object val) {
      if (val instanceof String) {
         String sval = (String) val;
         return STR_SIZE(sval.length());
      } else
         return INT_SIZE;
   }

   /**
    * Returns the LSN of the most recent log record.
    * As implemented, the LSN is the block number where the record is stored.
    * Thus every log record in a block has the same LSN.
    *
    * @return the LSN of the most recent log record
    */
   private int currentLSN() {
      // TODO: Set LSN to -1 if there isn't a block here
      return currentblk.number();
   }

   /**
    * Writes the current page to the log file.
    */
   private void flush() {
      // TODO: buffMgr.flushAll(currentLSN())
//      mypage.write(currentblk);
      bufferMgr.flushAll(currentLSN());
   }

   /**
    * Clear the current page, and append it to the log file.
    */
   private void appendNewBlock() {
       // TODO: Not necessary to call setLastRecordPosition
      currentpos = INT_SIZE;
      // TODO: buf.pinNew(logfile, LogFormatter)
      // TODO: Make LogFormatter
//      currentblk = mypage.append(logfile);
      if (mybuf != null) {
         bufferMgr.unpin(mybuf);
      }
      mybuf = bufferMgr.pinNew(logfile, fmtr);
      currentblk = mybuf.block();
      setLastRecordPosition(0);
   }

   /**
    * Sets up a circular chain of pointers to the records in the page.
    * There is an integer added to the end of each log record
    * whose value is the offset of the previous log record.
    * The first four bytes of the page contain an integer whose value
    * is the offset of the integer for the last log record in the page.
    */
   private void finalizeRecord() {
      // TODO: buf.setInt(currentpos, getLastRecordPosition(), currentLSN(), -1)
//      mypage.setInt(currentpos, getLastRecordPosition());
      mybuf.setInt(currentpos, getLastRecordPosition(), currentLSN(), -1);
      setLastRecordPosition(currentpos);
      currentpos += INT_SIZE;
   }

   public void printLogPageBuffer() {
//      Write   a   method   called   “ printLogPageBuffer() ”   and   call   it   to   output   the   log   page   on   the console.   The   output   can   be   in   the   following   format.
//      Buffer   number   pinned   to   the   log   block:   xxx
//      Contents   of   buffer   xxx:
//      Verify  if  the  contents  of  the  buffer  are    as  expected  based  on  the  updates  made  using setInt()   and   setString()   methods.

//      private Page contents = new Page();
//      private Block blk = null;
//      private int pins = 0;
//      private int modifiedBy = -1;  // negative means not modified
//      private int logSequenceNumber = -1; // negative means no corresponding log record


      System.out.println("----------------------------------------------");
      System.out.println("  Buffer number pinned to the log block: " + mybuf.block().number()); //.getPins());

      ByteBuffer byteBuffer = mybuf.getContents();
      byte[] bb = new byte[byteBuffer.remaining()];
      String s = new String(bb, StandardCharsets.UTF_8);

      System.out.println("  Contents of buffer:    " + s);
      System.out.print("  Values of buffer ints: ");

      for ( int i = 0; i < bb.length; i ++){
         System.out.print(bb[i]);
      }

      System.out.println("");


   }

   private int getLastRecordPosition() {
      // TODO: buf.getInt(LAST_POS)
//      return mypage.getInt(LAST_POS);
      return mybuf.getInt(LAST_POS);
   }

   private void setLastRecordPosition(int pos) {
      // TODO: buf.setInt(LAST_POS, pos, currentLSN(), -1)
//      mypage.setInt(LAST_POS, pos);
      mybuf.setInt(LAST_POS, pos, currentLSN(), -1);
   }

}
