/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.lib.parser.log;

import com.streamsets.pipeline.api.OnRecordError;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.api.Stage;
import com.streamsets.pipeline.lib.io.OverrunReader;
import com.streamsets.pipeline.lib.parser.DataParser;
import com.streamsets.pipeline.lib.parser.DataParserException;
import com.streamsets.pipeline.sdk.ContextInfoCreator;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class TestRegexParser {

  private static final String LOG_LINE = "127.0.0.1 ss h [10/Oct/2000:13:55:36 -0700] \"GET /apache_pb.gif HTTP/1.0\" " +
    "200 2326 Hello";
  private static final String REGEX = "^(\\S+) (\\S+) (\\S+) \\[([\\w:/]+\\s[+\\-]\\d{4})\\] \"(\\S+ \\S+ \\S+)\" (\\d{3}) (\\d+)";

  private static final Map<String, Integer> FIELD_TO_GROUP_MAP = new HashMap<>();

  static {
    FIELD_TO_GROUP_MAP.put("remoteHost", 1);
    FIELD_TO_GROUP_MAP.put("logName", 2);
    FIELD_TO_GROUP_MAP.put("remoteUser", 3);
    FIELD_TO_GROUP_MAP.put("requestTime", 4);
    FIELD_TO_GROUP_MAP.put("request", 5);
    FIELD_TO_GROUP_MAP.put("status", 6);
    FIELD_TO_GROUP_MAP.put("bytesSent", 7);
  }

  private Stage.Context getContext() {
    return ContextInfoCreator.createSourceContext("i", false, OnRecordError.TO_ERROR,
      Collections.<String>emptyList());
  }

  @Test
  public void testParse() throws Exception {
    OverrunReader reader = new OverrunReader(new StringReader(LOG_LINE), 1000, true, false);
    DataParser parser = new RegexParser(getContext(), "id", reader, 0, 1000, true, Pattern.compile(REGEX),
      FIELD_TO_GROUP_MAP);
    Assert.assertEquals(0, parser.getOffset());
    Record record = parser.parse();
    Assert.assertNotNull(record);

    Assert.assertEquals("id::0", record.getHeader().getSourceId());

    Assert.assertEquals(LOG_LINE, record.get().getValueAsMap().get("originalLine").getValueAsString());

    Assert.assertFalse(record.has("/truncated"));

    Assert.assertEquals(88, parser.getOffset());

    Assert.assertTrue(record.has("/remoteHost"));
    Assert.assertEquals("127.0.0.1", record.get("/remoteHost").getValueAsString());

    Assert.assertTrue(record.has("/logName"));
    Assert.assertEquals("ss", record.get("/logName").getValueAsString());

    Assert.assertTrue(record.has("/remoteUser"));
    Assert.assertEquals("h", record.get("/remoteUser").getValueAsString());

    Assert.assertTrue(record.has("/requestTime"));
    Assert.assertEquals("10/Oct/2000:13:55:36 -0700", record.get("/requestTime").getValueAsString());

    Assert.assertTrue(record.has("/request"));
    Assert.assertEquals("GET /apache_pb.gif HTTP/1.0", record.get("/request").getValueAsString());

    Assert.assertTrue(record.has("/status"));
    Assert.assertEquals("200", record.get("/status").getValueAsString());

    Assert.assertTrue(record.has("/bytesSent"));
    Assert.assertEquals("2326", record.get("/bytesSent").getValueAsString());

    parser.close();
  }

  @Test
  public void testParseWithOffset() throws Exception {
    OverrunReader reader = new OverrunReader(new StringReader(
      "Hello\n" + LOG_LINE), 1000, true, false);
    DataParser parser = new RegexParser(getContext(), "id", reader, 6, 1000, true, Pattern.compile(REGEX),
      FIELD_TO_GROUP_MAP);
    Assert.assertEquals(6, parser.getOffset());
    Record record = parser.parse();
    Assert.assertNotNull(record);

    Assert.assertEquals("id::6", record.getHeader().getSourceId());

    Assert.assertEquals(LOG_LINE, record.get().getValueAsMap().get("originalLine").getValueAsString());

    Assert.assertFalse(record.has("/truncated"));

    Assert.assertEquals(94, parser.getOffset());

    Assert.assertTrue(record.has("/remoteHost"));
    Assert.assertEquals("127.0.0.1", record.get("/remoteHost").getValueAsString());

    Assert.assertTrue(record.has("/logName"));
    Assert.assertEquals("ss", record.get("/logName").getValueAsString());

    Assert.assertTrue(record.has("/remoteUser"));
    Assert.assertEquals("h", record.get("/remoteUser").getValueAsString());

    Assert.assertTrue(record.has("/requestTime"));
    Assert.assertEquals("10/Oct/2000:13:55:36 -0700", record.get("/requestTime").getValueAsString());

    Assert.assertTrue(record.has("/request"));
    Assert.assertEquals("GET /apache_pb.gif HTTP/1.0", record.get("/request").getValueAsString());

    Assert.assertTrue(record.has("/status"));
    Assert.assertEquals("200", record.get("/status").getValueAsString());

    Assert.assertTrue(record.has("/bytesSent"));
    Assert.assertEquals("2326", record.get("/bytesSent").getValueAsString());

    record = parser.parse();
    Assert.assertNull(record);

    Assert.assertEquals(-1, parser.getOffset());
    parser.close();
  }

  @Test(expected = IOException.class)
  public void testClose() throws Exception {
    OverrunReader reader = new OverrunReader(new StringReader("Hello\nByte"), 1000, true, false);
    DataParser parser = new RegexParser(getContext(), "id", reader, 0, 1000, false, Pattern.compile(REGEX),
      FIELD_TO_GROUP_MAP);
    parser.close();
    parser.parse();
  }

  @Test(expected = DataParserException.class)
  public void testTruncate() throws Exception {
    OverrunReader reader = new OverrunReader(new StringReader(
      LOG_LINE), 1000, true, false);
    DataParser parser = new RegexParser(getContext(), "id", reader, 0, 25, true, Pattern.compile(REGEX),
      FIELD_TO_GROUP_MAP); //cut short to 25
    Assert.assertEquals(0, parser.getOffset());
    try {
      parser.parse();
    } finally {
      parser.close();
    }
  }

  @Test(expected = DataParserException.class)
  public void testParseNonLogLine() throws Exception {
    OverrunReader reader = new OverrunReader(new StringReader(
      "127.0.0.1 ss h [10/Oct/2000:13:55:36 -0700] This is a log line that does not confirm to common log format"),
      1000, true, false);
    DataParser parser = new RegexParser(getContext(), "id", reader, 0, 1000, true, Pattern.compile(REGEX),
      FIELD_TO_GROUP_MAP);
    Assert.assertEquals(0, parser.getOffset());
    try {
      parser.parse();
    } finally {
      parser.close();
    }
  }
}
