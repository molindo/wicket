/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.wicket.util.string;

import junit.framework.TestCase;

import org.junit.Assert;

/**
 * Tests {@link JavascriptStripper}
 * 
 * @author <a href="mailto:jbq@apache.org">Jean-Baptiste Quenot</a>
 */
public class JavascriptStripperTest extends TestCase
{
	public void testUNIXWICKET501()
	{
		String s = JavascriptStripper.stripCommentsAndWhitespace("    // Handle the common XPath // expression\n    if ( !t.indexOf(\"//\") ) {");
		assertEquals(" \n if ( !t.indexOf(\"//\") ) {", s);
	}

	public void testDOSWICKET501()
	{
		String s = JavascriptStripper.stripCommentsAndWhitespace("    // Handle the common XPath // expression\r\n    if ( !t.indexOf(\"//\") ) {");
		assertEquals(" \r\nif ( !t.indexOf(\"//\") ) {", s);
	}

	public void testMACWICKET501()
	{
		String s = JavascriptStripper.stripCommentsAndWhitespace("    // Handle the common XPath // expression\r    if ( !t.indexOf(\"//\") ) {");
		assertEquals(" \r if ( !t.indexOf(\"//\") ) {", s);
	}

	public void testRegexp()
	{
		String s = JavascriptStripper.stripCommentsAndWhitespace("    t = jQuery.trim(t).replace( /^\\/\\//i, \"\" );");
		assertEquals(" t = jQuery.trim(t).replace( /^\\/\\//i, \"\" );", s);
	}

	public void testRegexp2()
	{
		String s = JavascriptStripper.stripCommentsAndWhitespace("foo.replace(/\"//*strip me*/, \"\"); // strip me\rdoFoo();");
		assertEquals("foo.replace(/\"/, \"\"); \rdoFoo();", s);
	}

	public void testRegexp3()
	{
		String s = JavascriptStripper.stripCommentsAndWhitespace("parseFloat( elem.filter.match(/alpha\\(opacity=(.*)\\)/)[1] ) / 100 : 1;\r//foo");
		assertEquals("parseFloat( elem.filter.match(/alpha\\(opacity=(.*)\\)/)[1] ) / 100 : 1;\r",
			s);
	}

	public void testRegexp4()
	{
		String before = " attr: /**/ //xyz\n /\\[((?:[\\w-]*:)?[\\w-]+)\\s*(?:([!^$*~|]?=)\\s*((['\"])([^\\4]*?)\\4|([^'\"][^\\]]*?)))?\\]/    after     regex";
		String after = JavascriptStripper.stripCommentsAndWhitespace(before);
		String expected = " attr:  \n /\\[((?:[\\w-]*:)?[\\w-]+)\\s*(?:([!^$*~|]?=)\\s*((['\"])([^\\4]*?)\\4|([^'\"][^\\]]*?)))?\\]/ after regex";
		assertEquals(expected, after);
		System.out.println(after);
	}

	public void testWICKET1806()
	{
		String before = "a = [ /^(\\[) *@?([\\w-]+) *([!*$^~=]*) *('?\"?)(.*?)\\4 *\\]/ ];    b()";
		String after = JavascriptStripper.stripCommentsAndWhitespace(before);
		String expected = "a = [ /^(\\[) *@?([\\w-]+) *([!*$^~=]*) *('?\"?)(.*?)\\4 *\\]/ ]; b()";

		assertEquals(expected, after);
	}

	public void testWICKET2060_1()
	{
		String before = "   a   b   c";
		String after = JavascriptStripper.stripCommentsAndWhitespace(before);
		String expected = " a b c";
		assertEquals(expected, after);
	}

	public void testWICKET2060_2()
	{
		String before = "   a \n  b   c\n\n";
		String after = JavascriptStripper.stripCommentsAndWhitespace(before);
		String expected = " a\nb c\n";
		assertEquals(expected, after);
	}

	public void testWICKET2060_3()
	{
		String before = "return  this.__unbind__(type, fn);";
		String after = JavascriptStripper.stripCommentsAndWhitespace(before);
		String expected = "return this.__unbind__(type, fn);";
		assertEquals(expected, after);
	}


	// @formatter:off
	public static String TESTSTRING2 =
         "   var test = function () {\n" +
         "   var c = \"!=\";\n" +
         "    /* from jquery 1.5.1 */\n" +
         "    if ( !l.match.PSEUDO.test(c) && !/!=/.test(c)) {\n" +
         "       alert(\"/something bad will happen */* \");\n" +
         "   }\n" +
         "\n" +
         "     var importantFunction = function () {alert(\"really important function \")}\n" +
         "   /*\n" +
         "     This code will be stripped\n" +
         "   */\n" +
         "\n" +
         "}" ;
	// @formatter:on

	public void testRegExThatStartsWithExclamationMark()
	{
		new JavascriptStripper();
		String result = JavascriptStripper.stripCommentsAndWhitespace(TESTSTRING2);
		Assert.assertFalse(result.contains("This code will be stripped"));
		Assert.assertTrue(result.contains("something bad will happen"));
		Assert.assertTrue(result.contains("really important function"));

		System.out.println(result);
	}

}
