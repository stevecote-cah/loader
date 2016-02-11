/*
 * Copyright (c) 2016 Stephan D. Cote' - All rights reserved.
 * 
 * This program and the accompanying materials are made available under the 
 * terms of the MIT License which accompanies this distribution, and is 
 * available at http://creativecommons.org/licenses/MIT/
 *
 * Contributors:
 *   Stephan D. Cote 
 *      - Initial concept and initial implementation
 */
package coyote.commons;

//import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.Calendar;
import java.util.GregorianCalendar;

import org.junit.Test;


/**
 * 
 */
public class CronEntryTest {
  static DecimalFormat MILLIS = new DecimalFormat( "000" );




  /**
   * Get a formatted string representing the difference between the two times.
   * 
   * @param millis number of elapsed milliseconds.
   * 
   * @return formatted string representing weeks, days, hours minutes and seconds.
   */
  public static String formatElapsed( long millis ) {
    if ( millis < 0 || millis == Long.MAX_VALUE ) {
      return "?";
    }

    long secondsInMilli = 1000;
    long minutesInMilli = secondsInMilli * 60;
    long hoursInMilli = minutesInMilli * 60;
    long daysInMilli = hoursInMilli * 24;
    long weeksInMilli = daysInMilli * 7;

    long elapsedWeeks = millis / weeksInMilli;
    millis = millis % weeksInMilli;

    long elapsedDays = millis / daysInMilli;
    millis = millis % daysInMilli;

    long elapsedHours = millis / hoursInMilli;
    millis = millis % hoursInMilli;

    long elapsedMinutes = millis / minutesInMilli;
    millis = millis % minutesInMilli;

    long elapsedSeconds = millis / secondsInMilli;
    millis = millis % secondsInMilli;

    StringBuilder b = new StringBuilder();

    if ( elapsedWeeks > 0 ) {
      b.append( elapsedWeeks );
      if ( elapsedWeeks > 1 )
        b.append( " wks " );
      else
        b.append( " wk " );
    }
    if ( elapsedDays > 0 ) {
      b.append( elapsedDays );
      if ( elapsedDays > 1 )
        b.append( " days " );
      else
        b.append( " day " );

    }
    if ( elapsedHours > 0 ) {
      b.append( elapsedHours );
      if ( elapsedHours > 1 )
        b.append( " hrs " );
      else
        b.append( " hr " );
    }
    if ( elapsedMinutes > 0 ) {
      b.append( elapsedMinutes );
      b.append( " min " );
    }
    b.append( elapsedSeconds );
    if ( millis > 0 ) {
      b.append( "." );
      b.append( MILLIS.format( millis ) );
    }
    b.append( " sec" );

    return b.toString();
  }




  /**
   * Test method for {@link coyote.commons.CronEntry#parse(java.lang.String)}.
   */
  @Test
  public void testParse() {
    CronEntry subject = null;

    try {
      subject = CronEntry.parse( null );
      //System.out.println(subject);
    } catch ( ParseException e ) {
      fail( e.getMessage() );
    }

    String pattern = "* * * * *";
    try {
      subject = CronEntry.parse( pattern );
      //System.out.println(subject);
    } catch ( ParseException e ) {
      fail( e.getMessage() );
    }

    pattern = "? ? ? ? ?";
    try {
      subject = CronEntry.parse( pattern );
      //System.out.println(subject);
    } catch ( ParseException e ) {
      fail( e.getMessage() );
    }

    pattern = "/15 3 * * ?";
    try {
      subject = CronEntry.parse( pattern );
      //System.out.println(subject);
    } catch ( ParseException e ) {
      fail( e.getMessage() );
    }

    pattern = "*/15 3 */2 * 1-6";
    try {
      subject = CronEntry.parse( pattern );
      //System.out.println(subject);
    } catch ( ParseException e ) {
      fail( e.getMessage() );
    }

    pattern = "B A D * *";
    try {
      subject = CronEntry.parse( pattern );
      //System.out.println(subject);
    } catch ( ParseException e ) {
      fail( e.getMessage() );
    }

    pattern = "";
    try {
      subject = CronEntry.parse( pattern );
      //System.out.println(subject);
    } catch ( ParseException e ) {
      fail( e.getMessage() );
    }

    pattern = "* * * * * * * * * * * * * *";
    try {
      subject = CronEntry.parse( pattern );
      //System.out.println(subject);
    } catch ( ParseException e ) {
      fail( e.getMessage() );
    }
  }




  /**
   * Test method for {@link coyote.commons.CronEntry#mayRunAt(java.util.Calendar)}.
   */
  @Test
  public void testMayRunAt() {
    StringBuffer b = new StringBuffer();
    Calendar cal = new GregorianCalendar();

    CronEntry subject = null;
    try {
      subject = CronEntry.parse( null );

      // set the minute pattern to the current minute
      subject.setMinutePattern( Integer.toString( cal.get( Calendar.MINUTE ) ) );
      subject.setHourPattern( Integer.toString( cal.get( Calendar.HOUR_OF_DAY ) ) );
      subject.setDayPattern( Integer.toString( cal.get( Calendar.DAY_OF_MONTH ) ) );
      subject.setMonthPattern( Integer.toString( cal.get( Calendar.MONTH ) + 1 ) );
      subject.setDayOfWeekPattern( Integer.toString( cal.get( Calendar.DAY_OF_WEEK ) - 1 ) );

      //System.out.println( subject );
      assertTrue( subject.mayRunAt( cal ) );
    } catch ( ParseException e ) {
      fail( e.getMessage() );
    }

  }




  /**
   * Test method for {@link coyote.commons.CronEntry#mayRunNow()}.
   */
  @Test
  public void testMayRunNow() {
    String pattern = "* * * * *";
    CronEntry subject = null;
    try {
      subject = CronEntry.parse( pattern );
      assertTrue( subject.mayRunNow() );

      subject = CronEntry.parse( null );
      Calendar cal = new GregorianCalendar();
      subject.setMinutePattern( Integer.toString( cal.get( Calendar.MINUTE ) ) );
      subject.setHourPattern( Integer.toString( cal.get( Calendar.HOUR_OF_DAY ) ) );
      subject.setDayPattern( Integer.toString( cal.get( Calendar.DAY_OF_MONTH ) ) );
      subject.setMonthPattern( Integer.toString( cal.get( Calendar.MONTH ) + 1 ) );
      subject.setDayOfWeekPattern( Integer.toString( cal.get( Calendar.DAY_OF_WEEK ) - 1 ) );
      assertTrue( subject.mayRunNow() );

      //System.out.println( subject );      
    } catch ( ParseException e ) {
      fail( e.getMessage() );
    }
  }




  /**
   * Test method for {@link coyote.commons.CronEntry#getNextTime()}.
   */
  @Test
  public void testGetNextTime() {
    CronEntry subject = new CronEntry();
    long millis;
    Calendar cal = new GregorianCalendar();

    try {
      // parse an entry which allows / accepts all dates and times
      subject = CronEntry.parse( null );

      // set the pattern to one hour in the future
      subject.setMinutePattern( Integer.toString( cal.get( Calendar.MINUTE ) ) );
      subject.setHourPattern( Integer.toString( cal.get( Calendar.HOUR_OF_DAY ) + 1 ) ); // adjustment
      subject.setDayPattern( Integer.toString( cal.get( Calendar.DAY_OF_MONTH ) ) );
      subject.setMonthPattern( Integer.toString( cal.get( Calendar.MONTH ) + 1 ) );// no adjustment
      subject.setDayOfWeekPattern( Integer.toString( cal.get( Calendar.DAY_OF_WEEK ) - 1 ) );// no adjustment
      assertFalse( subject.mayRunNow() );
      //System.out.println( subject.toString() );

      // this should be an hour or a little less than now
      millis = subject.getNextTime();
      long now = System.currentTimeMillis();
      assertTrue( ( millis - now ) <= 3600000 );

      //Date date = new Date(millis);
      //System.out.println( millis + " - " + date );
    } catch ( ParseException e ) {
      fail( e.getMessage() );
    }
  }




  /**
   * Test method for {@link coyote.commons.CronEntry#getNextInterval()}.
   */
  @Test
  public void testGetNextInterval() {
    CronEntry subject = new CronEntry();
    long millis;
    Calendar cal = new GregorianCalendar();
    System.out.println( subject.dump() );

    // set the pattern to one hour in the future
    subject.setMinutePattern( Integer.toString( cal.get( Calendar.MINUTE ) ) );
    subject.setHourPattern( Integer.toString( cal.get( Calendar.HOUR_OF_DAY ) + 1 ) ); // adjustment
    subject.setDayPattern( Integer.toString( cal.get( Calendar.DAY_OF_MONTH ) ) );
    subject.setMonthPattern( Integer.toString( cal.get( Calendar.MONTH ) + 1 ) );// no adjustment
    subject.setDayOfWeekPattern( Integer.toString( cal.get( Calendar.DAY_OF_WEEK ) - 1 ) );// no adjustment
    assertFalse( subject.mayRunNow() );
    //System.out.println( subject.toString() );

    millis = subject.getNextInterval();
    //System.out.println( millis + " - " + formatElapsed( millis ) );

    // every 30 minute test
    subject = new CronEntry();
    subject.setMinutePattern( "0,30" );
    //millis = subject.getNextInterval();
    //System.out.println( millis );
    //assertTrue(millis<=(1800000));
    //System.out.println( millis + " - " + formatElapsed( millis ) );

    subject = new CronEntry();
    subject.setMinutePattern( "*/30" );
    //millis = subject.getNextInterval();
    //System.out.println( millis + " - " + formatElapsed( millis ) );

    System.out.println( subject.dump() );
  }

}
