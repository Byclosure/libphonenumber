/*
 * Copyright (C) 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.i18n.phonenumbers;

import com.google.i18n.phonenumbers.Phonemetadata.NumberFormat;
import com.google.i18n.phonenumbers.Phonemetadata.PhoneMetadata;
import com.google.i18n.phonenumbers.Phonemetadata.PhoneMetadataCollection;
import com.google.i18n.phonenumbers.Phonemetadata.PhoneNumberDesc;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber.CountryCodeSource;

import java.io.InputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for international phone numbers. Functionality includes formatting, parsing and
 * validation.
 *
 * @author Shaopeng Jia
 * @author Lara Rennie
 */
public class PhoneNumberUtil {
  // The minimum and maximum length of the national significant number.
  private static final int MIN_LENGTH_FOR_NSN = 3;
  private static final int MAX_LENGTH_FOR_NSN = 15;
  private static final String META_DATA_FILE_PREFIX =
      "/com/google/i18n/phonenumbers/data/PhoneNumberMetadataProto";
  private String currentFilePrefix = META_DATA_FILE_PREFIX;
  private static final Logger LOGGER = Logger.getLogger(PhoneNumberUtil.class.getName());

  // A mapping from a country code to the region codes which denote the country/region
  // represented by that country code. In the case of multiple countries sharing a calling code,
  // such as the NANPA countries, the one indicated with "isMainCountryForCode" in the metadata
  // should be first.
  // The initial capacity is set to 300 as there are roughly 200 different
  // country codes, and this offers a load factor of roughly 0.75.

  private Map<Integer, List<String> > countryCodeToRegionCodeMap =
      new HashMap<Integer, List<String> >(300);

  // The set of countries the library supports.
  // There are roughly 220 of them and we set the initial capacity of the HashSet to 300 to offer a
  // load factor of roughly 0.75.
  private final Set<String> supportedCountries = new HashSet<String>(300);


  // The set of countries that share country code 1.
  // There are roughly 26 countries of them and we set the initial capacity of the HashSet to 35
  // to offer a load factor of roughly 0.75.
  private final Set<String> nanpaCountries = new HashSet<String>(35);
  private static final int NANPA_COUNTRY_CODE = 1;

  // The PLUS_SIGN signifies the international prefix.
  static final char PLUS_SIGN = '+';

  // These mappings map a character (key) to a specific digit that should replace it for
  // normalization purposes. Non-European digits that may be used in phone numbers are mapped to a
  // European equivalent.
  static final Map<Character, Character> DIGIT_MAPPINGS;

  // Only upper-case variants of alpha characters are stored.
  private static final Map<Character, Character> ALPHA_MAPPINGS;

  // For performance reasons, amalgamate both into one map.
  private static final Map<Character, Character> ALL_NORMALIZATION_MAPPINGS;

  static {
    HashMap<Character, Character> digitMap = new HashMap<Character, Character>(50);
    digitMap.put('0', '0');
    digitMap.put('\uFF10', '0');  // Fullwidth digit 0
    digitMap.put('\u0660', '0');  // Arabic-indic digit 0
    digitMap.put('1', '1');
    digitMap.put('\uFF11', '1');  // Fullwidth digit 1
    digitMap.put('\u0661', '1');  // Arabic-indic digit 1
    digitMap.put('2', '2');
    digitMap.put('\uFF12', '2');  // Fullwidth digit 2
    digitMap.put('\u0662', '2');  // Arabic-indic digit 2
    digitMap.put('3', '3');
    digitMap.put('\uFF13', '3');  // Fullwidth digit 3
    digitMap.put('\u0663', '3');  // Arabic-indic digit 3
    digitMap.put('4', '4');
    digitMap.put('\uFF14', '4');  // Fullwidth digit 4
    digitMap.put('\u0664', '4');  // Arabic-indic digit 4
    digitMap.put('5', '5');
    digitMap.put('\uFF15', '5');  // Fullwidth digit 5
    digitMap.put('\u0665', '5');  // Arabic-indic digit 5
    digitMap.put('6', '6');
    digitMap.put('\uFF16', '6');  // Fullwidth digit 6
    digitMap.put('\u0666', '6');  // Arabic-indic digit 6
    digitMap.put('7', '7');
    digitMap.put('\uFF17', '7');  // Fullwidth digit 7
    digitMap.put('\u0667', '7');  // Arabic-indic digit 7
    digitMap.put('8', '8');
    digitMap.put('\uFF18', '8');  // Fullwidth digit 8
    digitMap.put('\u0668', '8');  // Arabic-indic digit 8
    digitMap.put('9', '9');
    digitMap.put('\uFF19', '9');  // Fullwidth digit 9
    digitMap.put('\u0669', '9');  // Arabic-indic digit 9
    DIGIT_MAPPINGS = Collections.unmodifiableMap(digitMap);

    HashMap<Character, Character> alphaMap = new HashMap<Character, Character>(40);
    alphaMap.put('A', '2');
    alphaMap.put('B', '2');
    alphaMap.put('C', '2');
    alphaMap.put('D', '3');
    alphaMap.put('E', '3');
    alphaMap.put('F', '3');
    alphaMap.put('G', '4');
    alphaMap.put('H', '4');
    alphaMap.put('I', '4');
    alphaMap.put('J', '5');
    alphaMap.put('K', '5');
    alphaMap.put('L', '5');
    alphaMap.put('M', '6');
    alphaMap.put('N', '6');
    alphaMap.put('O', '6');
    alphaMap.put('P', '7');
    alphaMap.put('Q', '7');
    alphaMap.put('R', '7');
    alphaMap.put('S', '7');
    alphaMap.put('T', '8');
    alphaMap.put('U', '8');
    alphaMap.put('V', '8');
    alphaMap.put('W', '9');
    alphaMap.put('X', '9');
    alphaMap.put('Y', '9');
    alphaMap.put('Z', '9');
    ALPHA_MAPPINGS = Collections.unmodifiableMap(alphaMap);

    HashMap<Character, Character> combinedMap = new HashMap<Character, Character>(100);
    combinedMap.putAll(alphaMap);
    combinedMap.putAll(digitMap);
    ALL_NORMALIZATION_MAPPINGS = Collections.unmodifiableMap(combinedMap);
  }

  // A list of all country codes where national significant numbers (excluding any national prefix)
  // exist that start with a leading zero.
  private static final Set<Integer> LEADING_ZERO_COUNTRIES;
  static {
    HashSet<Integer> aSet = new HashSet<Integer>(10);
    aSet.add(39);  // Italy
    aSet.add(47);  // Norway
    aSet.add(225);  // Cote d'Ivoire
    aSet.add(227);  // Niger
    aSet.add(228);  // Togo
    aSet.add(241);  // Gabon
    aSet.add(379);  // Vatican City
    LEADING_ZERO_COUNTRIES = Collections.unmodifiableSet(aSet);
  }

  // Pattern that makes it easy to distinguish whether a country has a unique international dialing
  // prefix or not. If a country has a unique international prefix (e.g. 011 in USA), it will be
  // represented as a string that contains a sequence of ASCII digits. If there are multiple
  // available international prefixes in a country, they will be represented as a regex string that
  // always contains character(s) other than ASCII digits.
  // Note this regex also includes tilde, which signals waiting for the tone.
  private static final Pattern UNIQUE_INTERNATIONAL_PREFIX =
      Pattern.compile("[\\d]+(?:[~\u2053\u223C\uFF5E][\\d]+)?");

  // Regular expression of acceptable punctuation found in phone numbers. This excludes punctuation
  // found as a leading character only.
  // This consists of dash characters, white space characters, full stops, slashes,
  // square brackets, parentheses and tildes. It also includes the letter 'x' as that is found as a
  // placeholder for carrier information in some phone numbers.
  private static final String VALID_PUNCTUATION = "-x\u2010-\u2015\u2212\uFF0D-\uFF0F " +
      "\u00A0\u200B\u2060\u3000()\uFF08\uFF09\uFF3B\uFF3D.\\[\\]/~\u2053\u223C\uFF5E";

  // Digits accepted in phone numbers
  private static final String VALID_DIGITS =
      Arrays.toString(DIGIT_MAPPINGS.keySet().toArray()).replaceAll(", ", "");
  // We accept alpha characters in phone numbers, ASCII only, upper and lower case.
  private static final String VALID_ALPHA =
      Arrays.toString(ALPHA_MAPPINGS.keySet().toArray()).replaceAll(", ", "") +
      Arrays.toString(ALPHA_MAPPINGS.keySet().toArray()).toLowerCase().replaceAll(", ", "");
  private static final String PLUS_CHARS = "+\uFF0B";
  private static final Pattern CAPTURING_DIGIT_PATTERN =
      Pattern.compile("([" + VALID_DIGITS + "])");

  // Regular expression of acceptable characters that may start a phone number for the purposes of
  // parsing. This allows us to strip away meaningless prefixes to phone numbers that may be
  // mistakenly given to us. This consists of digits, the plus symbol and arabic-indic digits. This
  // does not contain alpha characters, although they may be used later in the number. It also does
  // not include other punctuation, as this will be stripped later during parsing and is of no
  // information value when parsing a number.
  private static final String VALID_START_CHAR = "[" + PLUS_CHARS + VALID_DIGITS + "]";
  static final Pattern VALID_START_CHAR_PATTERN = Pattern.compile(VALID_START_CHAR);

  // Regular expression of characters typically used to start a second phone number for the purposes
  // of parsing. This allows us to strip off parts of the number that are actually the start of
  // another number, such as for: (530) 583-6985 x302/x2303 -> the second extension here makes this
  // actually two phone numbers, (530) 583-6985 x302 and (530) 583-6985 x2303. We remove the second
  // extension so that the first number is parsed correctly.
  private static final String SECOND_NUMBER_START = "[\\\\/] *x";
  private static final Pattern SECOND_NUMBER_START_PATTERN = Pattern.compile(SECOND_NUMBER_START);

  // Regular expression of trailing characters that we want to remove. We remove all characters that
  // are not alpha or numerical characters. The hash character is retained here, as it may signify
  // the previous block was an extension.
  private static final String UNWANTED_END_CHARS = "[[\\P{N}&&\\P{L}]&&[^#]]+$";
  private static final Pattern UNWANTED_END_CHAR_PATTERN = Pattern.compile(UNWANTED_END_CHARS);

  // We use this pattern to check if the phone number has at least three letters in it - if so, then
  // we treat it as a number where some phone-number digits are represented by letters.
  private static final Pattern VALID_ALPHA_PHONE_PATTERN = Pattern.compile("(?:.*?[A-Za-z]){3}.*");

  // Regular expression of viable phone numbers. This is location independent. Checks we have at
  // least three leading digits, and only valid punctuation, alpha characters and
  // digits in the phone number. Does not include extension data.
  // The symbol 'x' is allowed here as valid punctuation since it is often used as a placeholder for
  // carrier codes, for example in Brazilian phone numbers.
  // Corresponds to the following:
  // plus_sign?([punctuation]*[digits]){3,}([punctuation]|[digits]|[alpha])*
  private static final String VALID_PHONE_NUMBER =
      "[" + PLUS_CHARS + "]?(?:[" + VALID_PUNCTUATION + "]*[" + VALID_DIGITS + "]){3,}[" +
      VALID_ALPHA + VALID_PUNCTUATION + VALID_DIGITS + "]*";

  // Default extension prefix to use when formatting. This will be put in front of any extension
  // component of the number, after the main national number is formatted. For example, if you wish
  // the default extension formatting to be " extn: 3456", then you should specify " extn: " here
  // as the default extension prefix. This can be overridden by country-specific preferences.
  private static final String DEFAULT_EXTN_PREFIX = " ext. ";

  // Regexp of all possible ways to write extensions, for use when parsing. This will be run as a
  // case-insensitive regexp match. Wide character versions are also provided after each ascii
  // version. There are two regular expressions here: the more generic one starts with optional
  // white space and ends with an optional full stop (.), followed by zero or more spaces/tabs and
  // then the numbers themselves. The other one covers the special case of American numbers where
  // the extension is written with a hash at the end, such as "- 503#".
  // Note that the only capturing groups should be around the digits that you want to capture as
  // part of the extension, or else parsing will fail!
  private static final String KNOWN_EXTN_PATTERNS = "[ \u00A0\\t,]*(?:ext(?:ensio)?n?|" +
      "\uFF45\uFF58\uFF54\uFF4E?|[,x\uFF58#\uFF03~\uFF5E]|int|anexo|\uFF49\uFF4E\uFF54)" +
      "[:\\.\uFF0E]?[ \u00A0\\t,-]*([" + VALID_DIGITS + "]{1,7})#?|[- ]+([" + VALID_DIGITS +
      "]{1,5})#";

  // Regexp of all known extension prefixes used by different countries followed by 1 or more valid
  // digits, for use when parsing.
  private static final Pattern EXTN_PATTERN =
      Pattern.compile("(?:" + KNOWN_EXTN_PATTERNS + ")$",
                      Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE);

  // We append optionally the extension pattern to the end here, as a valid phone number may
  // have an extension prefix appended, followed by 1 or more digits.
  private static final Pattern VALID_PHONE_NUMBER_PATTERN =
      Pattern.compile(VALID_PHONE_NUMBER + "(?:" + KNOWN_EXTN_PATTERNS + ")?",
                      Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE);

  private static final Pattern NON_DIGITS_PATTERN = Pattern.compile("(\\D+)");
  private static final Pattern FIRST_GROUP_PATTERN = Pattern.compile("(\\$1)");
  private static final Pattern NP_PATTERN = Pattern.compile("\\$NP");
  private static final Pattern FG_PATTERN = Pattern.compile("\\$FG");
  private static final Pattern CC_PATTERN = Pattern.compile("\\$CC");

  private static PhoneNumberUtil instance = null;

  // A mapping from a region code to the PhoneMetadata for that region.
  private Map<String, PhoneMetadata> countryToMetadataMap =
      new HashMap<String, PhoneMetadata>();

  // A cache for frequently used country-specific regular expressions.
  // As most people use phone numbers primarily from one to two countries, and there are roughly 60
  // regular expressions needed, the initial capacity of 100 offers a rough load factor of 0.75.
  private RegexCache regexCache = new RegexCache(100);

  /**
   * INTERNATIONAL and NATIONAL formats are consistent with the definition in ITU-T Recommendation
   * E. 123. For example, the number of the Google Zurich office will be written as
   * "+41 44 668 1800" in INTERNATIONAL format, and as "044 668 1800" in NATIONAL format.
   * E164 format is as per INTERNATIONAL format but with no formatting applied, e.g. +41446681800.
   *
   * Note: If you are considering storing the number in a neutral format, you are highly advised to
   * use the phonenumber.proto.
   */
  public enum PhoneNumberFormat {
    E164,
    INTERNATIONAL,
    NATIONAL
  }

  /**
   * Type of phone numbers.
   */
  public enum PhoneNumberType {
    FIXED_LINE,
    MOBILE,
    // In some countries (e.g. the USA), it is impossible to distinguish between fixed-line and
    // mobile numbers by looking at the phone number itself.
    FIXED_LINE_OR_MOBILE,
    // Freephone lines
    TOLL_FREE,
    PREMIUM_RATE,
    // The cost of this call is shared between the caller and the recipient, and is hence typically
    // less than PREMIUM_RATE calls. See // http://en.wikipedia.org/wiki/Shared_Cost_Service for
    // more information.
    SHARED_COST,
    // Voice over IP numbers. This includes TSoIP (Telephony Service over IP).
    VOIP,
    // A personal number is associated with a particular person, and may be routed to either a
    // MOBILE or FIXED_LINE number. Some more information can be found here:
    // http://en.wikipedia.org/wiki/Personal_Numbers
    PERSONAL_NUMBER,
    // A phone number is of type UNKNOWN when it does not fit any of the known patterns for a
    // specific country.
    UNKNOWN
  }

  /**
   * Types of phone number matches. See detailed description beside the isNumberMatch() method.
   */
  public enum MatchType {
    NO_MATCH,
    SHORT_NSN_MATCH,
    NSN_MATCH,
    EXACT_MATCH,
  }

  /**
   * Possible outcomes when testing if a PhoneNumber is possible.
   */
  public enum ValidationResult {
    IS_POSSIBLE,
    INVALID_COUNTRY_CODE,
    TOO_SHORT,
    TOO_LONG,
  }

  /**
   * This class implements a singleton, so the only constructor is private.
   */
  private PhoneNumberUtil() {
  }

  private void initializeCountryCodeToRegionCodeMap() {
    countryCodeToRegionCodeMap.clear();
    ArrayList<String> listWithRegionCode = new ArrayList<String>(24);
    listWithRegionCode.add("US");
    listWithRegionCode.add("AG");
    listWithRegionCode.add("AI");
    listWithRegionCode.add("AS");
    listWithRegionCode.add("BB");
    listWithRegionCode.add("BM");
    listWithRegionCode.add("BS");
    listWithRegionCode.add("CA");
    listWithRegionCode.add("DM");
    listWithRegionCode.add("DO");
    listWithRegionCode.add("GD");
    listWithRegionCode.add("GU");
    listWithRegionCode.add("JM");
    listWithRegionCode.add("KN");
    listWithRegionCode.add("KY");
    listWithRegionCode.add("LC");
    listWithRegionCode.add("MP");
    listWithRegionCode.add("MS");
    listWithRegionCode.add("PR");
    listWithRegionCode.add("TC");
    listWithRegionCode.add("TT");
    listWithRegionCode.add("VC");
    listWithRegionCode.add("VG");
    listWithRegionCode.add("VI");
    countryCodeToRegionCodeMap.put(1, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(2);
    listWithRegionCode.add("RU");
    listWithRegionCode.add("KZ");
    countryCodeToRegionCodeMap.put(7, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("EG");
    countryCodeToRegionCodeMap.put(20, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("ZA");
    countryCodeToRegionCodeMap.put(27, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("NL");
    countryCodeToRegionCodeMap.put(31, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("GR");
    countryCodeToRegionCodeMap.put(30, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("ES");
    countryCodeToRegionCodeMap.put(34, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("BE");
    countryCodeToRegionCodeMap.put(32, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("FR");
    countryCodeToRegionCodeMap.put(33, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("IT");
    countryCodeToRegionCodeMap.put(39, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("HU");
    countryCodeToRegionCodeMap.put(36, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("AT");
    countryCodeToRegionCodeMap.put(43, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("RO");
    countryCodeToRegionCodeMap.put(40, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("CH");
    countryCodeToRegionCodeMap.put(41, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("SE");
    countryCodeToRegionCodeMap.put(46, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("NO");
    countryCodeToRegionCodeMap.put(47, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(4);
    listWithRegionCode.add("GB");
    listWithRegionCode.add("GG");
    listWithRegionCode.add("IM");
    listWithRegionCode.add("JE");
    countryCodeToRegionCodeMap.put(44, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("DK");
    countryCodeToRegionCodeMap.put(45, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("PE");
    countryCodeToRegionCodeMap.put(51, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("DE");
    countryCodeToRegionCodeMap.put(49, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("PL");
    countryCodeToRegionCodeMap.put(48, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("BR");
    countryCodeToRegionCodeMap.put(55, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("AR");
    countryCodeToRegionCodeMap.put(54, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("CU");
    countryCodeToRegionCodeMap.put(53, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("MX");
    countryCodeToRegionCodeMap.put(52, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("VE");
    countryCodeToRegionCodeMap.put(58, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("CO");
    countryCodeToRegionCodeMap.put(57, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("CL");
    countryCodeToRegionCodeMap.put(56, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("PH");
    countryCodeToRegionCodeMap.put(63, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("ID");
    countryCodeToRegionCodeMap.put(62, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("AU");
    countryCodeToRegionCodeMap.put(61, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("MY");
    countryCodeToRegionCodeMap.put(60, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("NZ");
    countryCodeToRegionCodeMap.put(64, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("SG");
    countryCodeToRegionCodeMap.put(65, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("TH");
    countryCodeToRegionCodeMap.put(66, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("VN");
    countryCodeToRegionCodeMap.put(84, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("CN");
    countryCodeToRegionCodeMap.put(86, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("JP");
    countryCodeToRegionCodeMap.put(81, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("KR");
    countryCodeToRegionCodeMap.put(82, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("AF");
    countryCodeToRegionCodeMap.put(93, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("PK");
    countryCodeToRegionCodeMap.put(92, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("MM");
    countryCodeToRegionCodeMap.put(95, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("LK");
    countryCodeToRegionCodeMap.put(94, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("IN");
    countryCodeToRegionCodeMap.put(91, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("TR");
    countryCodeToRegionCodeMap.put(90, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("IR");
    countryCodeToRegionCodeMap.put(98, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(3);
    listWithRegionCode.add("GP");
    listWithRegionCode.add("BL");
    listWithRegionCode.add("MF");
    countryCodeToRegionCodeMap.put(590, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("BO");
    countryCodeToRegionCodeMap.put(591, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("EC");
    countryCodeToRegionCodeMap.put(593, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("GY");
    countryCodeToRegionCodeMap.put(592, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("PY");
    countryCodeToRegionCodeMap.put(595, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("GF");
    countryCodeToRegionCodeMap.put(594, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("SR");
    countryCodeToRegionCodeMap.put(597, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("MQ");
    countryCodeToRegionCodeMap.put(596, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("AN");
    countryCodeToRegionCodeMap.put(599, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("UY");
    countryCodeToRegionCodeMap.put(598, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("NC");
    countryCodeToRegionCodeMap.put(687, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("KI");
    countryCodeToRegionCodeMap.put(686, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("WS");
    countryCodeToRegionCodeMap.put(685, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("NU");
    countryCodeToRegionCodeMap.put(683, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("CK");
    countryCodeToRegionCodeMap.put(682, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("WF");
    countryCodeToRegionCodeMap.put(681, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("PW");
    countryCodeToRegionCodeMap.put(680, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("FJ");
    countryCodeToRegionCodeMap.put(679, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("VU");
    countryCodeToRegionCodeMap.put(678, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("SB");
    countryCodeToRegionCodeMap.put(677, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("TO");
    countryCodeToRegionCodeMap.put(676, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("PG");
    countryCodeToRegionCodeMap.put(675, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("NR");
    countryCodeToRegionCodeMap.put(674, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("BN");
    countryCodeToRegionCodeMap.put(673, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("NF");
    countryCodeToRegionCodeMap.put(672, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("MH");
    countryCodeToRegionCodeMap.put(692, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("TK");
    countryCodeToRegionCodeMap.put(690, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("FM");
    countryCodeToRegionCodeMap.put(691, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("TV");
    countryCodeToRegionCodeMap.put(688, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("PF");
    countryCodeToRegionCodeMap.put(689, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("TL");
    countryCodeToRegionCodeMap.put(670, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("GM");
    countryCodeToRegionCodeMap.put(220, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("SN");
    countryCodeToRegionCodeMap.put(221, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("MR");
    countryCodeToRegionCodeMap.put(222, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("ML");
    countryCodeToRegionCodeMap.put(223, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("TN");
    countryCodeToRegionCodeMap.put(216, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("LY");
    countryCodeToRegionCodeMap.put(218, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("MA");
    countryCodeToRegionCodeMap.put(212, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("DZ");
    countryCodeToRegionCodeMap.put(213, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("ST");
    countryCodeToRegionCodeMap.put(239, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("CV");
    countryCodeToRegionCodeMap.put(238, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("CM");
    countryCodeToRegionCodeMap.put(237, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("CF");
    countryCodeToRegionCodeMap.put(236, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("TD");
    countryCodeToRegionCodeMap.put(235, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("NG");
    countryCodeToRegionCodeMap.put(234, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("GH");
    countryCodeToRegionCodeMap.put(233, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("SL");
    countryCodeToRegionCodeMap.put(232, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("LR");
    countryCodeToRegionCodeMap.put(231, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("MU");
    countryCodeToRegionCodeMap.put(230, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("BJ");
    countryCodeToRegionCodeMap.put(229, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("TG");
    countryCodeToRegionCodeMap.put(228, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("NE");
    countryCodeToRegionCodeMap.put(227, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("BF");
    countryCodeToRegionCodeMap.put(226, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("CI");
    countryCodeToRegionCodeMap.put(225, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("GN");
    countryCodeToRegionCodeMap.put(224, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("KE");
    countryCodeToRegionCodeMap.put(254, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("TZ");
    countryCodeToRegionCodeMap.put(255, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("SO");
    countryCodeToRegionCodeMap.put(252, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("DJ");
    countryCodeToRegionCodeMap.put(253, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("RW");
    countryCodeToRegionCodeMap.put(250, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("ET");
    countryCodeToRegionCodeMap.put(251, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("SC");
    countryCodeToRegionCodeMap.put(248, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("SD");
    countryCodeToRegionCodeMap.put(249, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("IO");
    countryCodeToRegionCodeMap.put(246, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("AO");
    countryCodeToRegionCodeMap.put(244, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("GW");
    countryCodeToRegionCodeMap.put(245, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("CG");
    countryCodeToRegionCodeMap.put(242, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("CD");
    countryCodeToRegionCodeMap.put(243, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("GQ");
    countryCodeToRegionCodeMap.put(240, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("GA");
    countryCodeToRegionCodeMap.put(241, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("MZ");
    countryCodeToRegionCodeMap.put(258, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("UG");
    countryCodeToRegionCodeMap.put(256, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("BI");
    countryCodeToRegionCodeMap.put(257, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(3);
    listWithRegionCode.add("RE");
    listWithRegionCode.add("TF");
    listWithRegionCode.add("YT");
    countryCodeToRegionCodeMap.put(262, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("ZW");
    countryCodeToRegionCodeMap.put(263, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("ZM");
    countryCodeToRegionCodeMap.put(260, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("MG");
    countryCodeToRegionCodeMap.put(261, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("LS");
    countryCodeToRegionCodeMap.put(266, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("BW");
    countryCodeToRegionCodeMap.put(267, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("NA");
    countryCodeToRegionCodeMap.put(264, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("MW");
    countryCodeToRegionCodeMap.put(265, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("SZ");
    countryCodeToRegionCodeMap.put(268, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("KM");
    countryCodeToRegionCodeMap.put(269, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("SH");
    countryCodeToRegionCodeMap.put(290, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("ER");
    countryCodeToRegionCodeMap.put(291, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("AW");
    countryCodeToRegionCodeMap.put(297, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("FO");
    countryCodeToRegionCodeMap.put(298, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("GL");
    countryCodeToRegionCodeMap.put(299, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("BD");
    countryCodeToRegionCodeMap.put(880, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("TW");
    countryCodeToRegionCodeMap.put(886, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("PT");
    countryCodeToRegionCodeMap.put(351, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("GI");
    countryCodeToRegionCodeMap.put(350, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("MD");
    countryCodeToRegionCodeMap.put(373, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("KP");
    countryCodeToRegionCodeMap.put(850, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("EE");
    countryCodeToRegionCodeMap.put(372, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("BY");
    countryCodeToRegionCodeMap.put(375, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("AM");
    countryCodeToRegionCodeMap.put(374, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("KH");
    countryCodeToRegionCodeMap.put(855, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("MO");
    countryCodeToRegionCodeMap.put(853, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("LV");
    countryCodeToRegionCodeMap.put(371, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("LT");
    countryCodeToRegionCodeMap.put(370, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("HK");
    countryCodeToRegionCodeMap.put(852, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("RS");
    countryCodeToRegionCodeMap.put(381, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("UA");
    countryCodeToRegionCodeMap.put(380, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("ME");
    countryCodeToRegionCodeMap.put(382, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("LA");
    countryCodeToRegionCodeMap.put(856, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("MC");
    countryCodeToRegionCodeMap.put(377, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("AD");
    countryCodeToRegionCodeMap.put(376, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("VA");
    countryCodeToRegionCodeMap.put(379, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("SM");
    countryCodeToRegionCodeMap.put(378, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("MT");
    countryCodeToRegionCodeMap.put(356, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("CY");
    countryCodeToRegionCodeMap.put(357, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("FI");
    countryCodeToRegionCodeMap.put(358, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("BG");
    countryCodeToRegionCodeMap.put(359, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("LU");
    countryCodeToRegionCodeMap.put(352, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("IE");
    countryCodeToRegionCodeMap.put(353, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("IS");
    countryCodeToRegionCodeMap.put(354, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("AL");
    countryCodeToRegionCodeMap.put(355, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("BA");
    countryCodeToRegionCodeMap.put(387, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("SI");
    countryCodeToRegionCodeMap.put(386, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("HR");
    countryCodeToRegionCodeMap.put(385, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("MK");
    countryCodeToRegionCodeMap.put(389, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("SK");
    countryCodeToRegionCodeMap.put(421, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("CZ");
    countryCodeToRegionCodeMap.put(420, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("LI");
    countryCodeToRegionCodeMap.put(423, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("TM");
    countryCodeToRegionCodeMap.put(993, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("TJ");
    countryCodeToRegionCodeMap.put(992, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("GE");
    countryCodeToRegionCodeMap.put(995, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("AZ");
    countryCodeToRegionCodeMap.put(994, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("KG");
    countryCodeToRegionCodeMap.put(996, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("UZ");
    countryCodeToRegionCodeMap.put(998, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("PM");
    countryCodeToRegionCodeMap.put(508, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("HT");
    countryCodeToRegionCodeMap.put(509, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("HN");
    countryCodeToRegionCodeMap.put(504, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("NI");
    countryCodeToRegionCodeMap.put(505, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("CR");
    countryCodeToRegionCodeMap.put(506, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("PA");
    countryCodeToRegionCodeMap.put(507, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("FK");
    countryCodeToRegionCodeMap.put(500, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("BZ");
    countryCodeToRegionCodeMap.put(501, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("MN");
    countryCodeToRegionCodeMap.put(976, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("GT");
    countryCodeToRegionCodeMap.put(502, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("SV");
    countryCodeToRegionCodeMap.put(503, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("NP");
    countryCodeToRegionCodeMap.put(977, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("AE");
    countryCodeToRegionCodeMap.put(971, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("PS");
    countryCodeToRegionCodeMap.put(970, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("OM");
    countryCodeToRegionCodeMap.put(968, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("BT");
    countryCodeToRegionCodeMap.put(975, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("QA");
    countryCodeToRegionCodeMap.put(974, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("BH");
    countryCodeToRegionCodeMap.put(973, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("IL");
    countryCodeToRegionCodeMap.put(972, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("SY");
    countryCodeToRegionCodeMap.put(963, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("JO");
    countryCodeToRegionCodeMap.put(962, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("LB");
    countryCodeToRegionCodeMap.put(961, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("MV");
    countryCodeToRegionCodeMap.put(960, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("YE");
    countryCodeToRegionCodeMap.put(967, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("SA");
    countryCodeToRegionCodeMap.put(966, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("KW");
    countryCodeToRegionCodeMap.put(965, listWithRegionCode);

    listWithRegionCode = new ArrayList<String>(1);
    listWithRegionCode.add("IQ");
    countryCodeToRegionCodeMap.put(964, listWithRegionCode);
  }

  private void init(String filePrefix) {
    currentFilePrefix = filePrefix;
    for (Integer countryCallingCode : countryCodeToRegionCodeMap.keySet()) {
      supportedCountries.addAll(countryCodeToRegionCodeMap.get(countryCallingCode));
    }
    nanpaCountries.addAll(countryCodeToRegionCodeMap.get(NANPA_COUNTRY_CODE));
  }

  private void loadMetadataForRegionFromFile(String filePrefix, String regionCode) {
    InputStream source =
        PhoneNumberUtil.class.getResourceAsStream(filePrefix + "_" + regionCode);
    ObjectInputStream in;
    try {
      in = new ObjectInputStream(source);
      PhoneMetadataCollection metadataCollection = new PhoneMetadataCollection();
      metadataCollection.readExternal(in);
      for (PhoneMetadata metadata : metadataCollection.getMetadataList()) {
        countryToMetadataMap.put(regionCode, metadata);
      }
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, e.toString());
    }
  }

  /**
   * Attempts to extract a possible number from the string passed in. This currently strips all
   * leading characters that could not be used to start a phone number. Characters that can be used
   * to start a phone number are defined in the VALID_START_CHAR_PATTERN. If none of these
   * characters are found in the number passed in, an empty string is returned. This function also
   * attempts to strip off any alternative extensions or endings if two or more are present, such as
   * in the case of: (530) 583-6985 x302/x2303. The second extension here makes this actually two
   * phone numbers, (530) 583-6985 x302 and (530) 583-6985 x2303. We remove the second extension so
   * that the first number is parsed correctly.
   *
   * @param number  the string that might contain a phone number
   * @return        the number, stripped of any non-phone-number prefix (such as "Tel:") or an empty
   *                string if no character used to start phone numbers (such as + or any digit) is
   *                found in the number
   */
  static String extractPossibleNumber(String number) {
    Matcher m = VALID_START_CHAR_PATTERN.matcher(number);
    if (m.find()) {
      number = number.substring(m.start());
      // Remove trailing non-alpha non-numerical characters.
      Matcher trailingCharsMatcher = UNWANTED_END_CHAR_PATTERN.matcher(number);
      if (trailingCharsMatcher.find()) {
        number = number.substring(0, trailingCharsMatcher.start());
      }
      // Check for extra numbers at the end.
      Matcher secondNumber = SECOND_NUMBER_START_PATTERN.matcher(number);
      if (secondNumber.find()) {
        number = number.substring(0, secondNumber.start());
      }
      return number;
    } else {
      return "";
    }
  }

  /**
   * Checks to see if the string of characters could possibly be a phone number at all. At the
   * moment, checks to see that the string begins with at least 3 digits, ignoring any punctuation
   * commonly found in phone numbers.
   * This method does not require the number to be normalized in advance - but does assume that
   * leading non-number symbols have been removed, such as by the method extractPossibleNumber.
   *
   * @param number  string to be checked for viability as a phone number
   * @return        true if the number could be a phone number of some sort, otherwise false
   */
  static boolean isViablePhoneNumber(String number) {
    if (number.length() < MIN_LENGTH_FOR_NSN) {
      return false;
    }
    Matcher m = VALID_PHONE_NUMBER_PATTERN.matcher(number);
    return m.matches();
  }

  /**
   * Normalizes a string of characters representing a phone number. This performs the following
   * conversions:
   *   Wide-ascii digits are converted to normal ASCII (European) digits.
   *   Letters are converted to their numeric representation on a telephone keypad. The keypad
   *       used here is the one defined in ITU Recommendation E.161. This is only done if there are
   *       3 or more letters in the number, to lessen the risk that such letters are typos -
   *       otherwise alpha characters are stripped.
   *   Punctuation is stripped.
   *   Arabic-Indic numerals are converted to European numerals.
   *
   * @param number  a string of characters representing a phone number
   * @return        the normalized string version of the phone number
   */
  static String normalize(String number) {
    Matcher m = VALID_ALPHA_PHONE_PATTERN.matcher(number);
    if (m.matches()) {
      return normalizeHelper(number, ALL_NORMALIZATION_MAPPINGS, true);
    } else {
      return normalizeHelper(number, DIGIT_MAPPINGS, true);
    }
  }

  /**
   * Normalizes a string of characters representing a phone number. This is a wrapper for
   * normalize(String number) but does in-place normalization of the StringBuffer provided.
   *
   * @param number  a StringBuffer of characters representing a phone number that will be normalized
   *     in place
   */
  static void normalize(StringBuffer number) {
    String normalizedNumber = normalize(number.toString());
    number.replace(0, number.length(), normalizedNumber);
  }

  /**
   * Normalizes a string of characters representing a phone number. This converts wide-ascii and
   * arabic-indic numerals to European numerals, and strips punctuation and alpha characters.
   *
   * @param number  a string of characters representing a phone number
   * @return        the normalized string version of the phone number
   */
  public static String normalizeDigitsOnly(String number) {
    return normalizeHelper(number, DIGIT_MAPPINGS, true);
  }

  /**
   * Converts all alpha characters in a number to their respective digits on a keypad, but retains
   * existing formatting. Also converts wide-ascii digits to normal ascii digits, and converts
   * Arabic-Indic numerals to European numerals.
   */
  public static String convertAlphaCharactersInNumber(String number) {
    return normalizeHelper(number, ALL_NORMALIZATION_MAPPINGS, false);
  }

  /**
   * Gets the length of the geographical area code from the national_number field of the PhoneNumber
   * object passed in, so that clients could use it to split a national significant number into
   * geographical area code and subscriber number. It works in such a way that the resultant
   * subscriber number should be diallable, at least on some devices. An example of how this could
   * be used:
   *
   * PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
   * PhoneNumber number = phoneUtil.parse("16502530000", RegionCode.US);
   * String nationalSignificantNumber = PhoneNumberUtil.getNationalSignificantNumber(number);
   * String areaCode;
   * String subscriberNumber;
   *
   * int areaCodeLength = phoneUtil.getLengthOfGeographicalAreaCode(number);
   * if (areaCodeLength > 0) {
   *   areaCode = nationalSignificantNumber.substring(0, areaCodeLength);
   *   subscriberNumber = nationalSignificantNumber.substring(areaCodeLength);
   * } else {
   *   areaCode = "";
   *   subscriberNumber = nationalSignificantNumber;
   * }
   *
   * N.B.: area code is a very ambiguous concept, so the I18N team generally recommends against
   * using it for most purposes, but recommends using the more general national_number instead. Read
   * the following carefully before deciding to use this method:
   *
   *  - geographical area codes change over time, and this method honors those changes; therefore,
   *    it doesn't guarantee the stability of the result it produces.
   *  - subscriber numbers may not be diallable from all devices (notably mobile devices, which
   *    typically requires the full national_number to be dialled in most countries).
   *  - most non-geographical numbers have no area codes.
   *  - some geographical numbers have no area codes.
   *
   * @param number  the PhoneNumber object for which clients want to know the length of the area
   *     code in the national_number field.
   * @return  the length of area code of the PhoneNumber object passed in.
   */
  public int getLengthOfGeographicalAreaCode(PhoneNumber number) {
    String regionCode = getRegionCodeForNumber(number);
    if (!isValidRegionCode(regionCode)) {
      return 0;
    }
    PhoneMetadata metadata = getMetadataForRegion(regionCode);
    // For NANPA countries, national prefix is the same as country code, but it is not stored in
    // the metadata.
    if (!metadata.hasNationalPrefix() && !isNANPACountry(regionCode)) {
      return 0;
    }

    PhoneNumberType type = getNumberTypeHelper(getNationalSignificantNumber(number),
                                               metadata);
    // Most numbers other than the two types below have to be dialled in full.
    if (type != PhoneNumberType.FIXED_LINE && type != PhoneNumberType.FIXED_LINE_OR_MOBILE) {
      return 0;
    }

    PhoneNumber copiedProto;
    if (number.hasExtension()) {
      // We don't want to alter the proto given to us, but we don't want to include the extension
      // when we format it, so we copy it and clear the extension here.
      copiedProto = new PhoneNumber();
      copiedProto.mergeFrom(number);
      copiedProto.clearExtension();
    } else {
      copiedProto = number;
    }

    String nationalSignificantNumber = format(copiedProto,
                                              PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
    String[] numberGroups = NON_DIGITS_PATTERN.split(nationalSignificantNumber);
    // The pattern will start with "+COUNTRY_CODE " so the first group will always be the empty
    // string (before the + symbol) and the second group will be the country code. The third group
    // will be area code if it is not the last group.
    if (numberGroups.length <= 3) {
      return 0;
    }
    return numberGroups[2].length();
  }

  /**
   * Normalizes a string of characters representing a phone number by replacing all characters found
   * in the accompanying map with the values therein, and stripping all other characters if
   * removeNonMatches is true.
   *
   * @param number                     a string of characters representing a phone number
   * @param normalizationReplacements  a mapping of characters to what they should be replaced by in
   *                                   the normalized version of the phone number
   * @param removeNonMatches           indicates whether characters that are not able to be replaced
   *                                   should be stripped from the number. If this is false, they
   *                                   will be left unchanged in the number.
   * @return  the normalized string version of the phone number
   */
  private static String normalizeHelper(String number,
                                        Map<Character, Character> normalizationReplacements,
                                        boolean removeNonMatches) {
    StringBuffer normalizedNumber = new StringBuffer(number.length());
    char[] numberAsCharArray = number.toCharArray();
    for (char character : numberAsCharArray) {
      Character newDigit = normalizationReplacements.get(Character.toUpperCase(character));
      if (newDigit != null) {
        normalizedNumber.append(newDigit);
      } else if (!removeNonMatches) {
        normalizedNumber.append(character);
      }
      // If neither of the above are true, we remove this character.
    }
    return normalizedNumber.toString();
  }

  static synchronized PhoneNumberUtil getInstance(
      String baseFileLocation,
      Map<Integer, List<String> > countryCodeToRegionCodeMap) {
    if (instance == null) {
      instance = new PhoneNumberUtil();
      instance.countryCodeToRegionCodeMap.clear();
      instance.countryCodeToRegionCodeMap = countryCodeToRegionCodeMap;
      instance.init(baseFileLocation);
    }
    return instance;
  }

  /**
   * Used for testing purposes only to reset the PhoneNumberUtil singleton to null.
   */
  static synchronized void resetInstance() {
    instance = null;
  }

  /**
   * Convenience method to enable tests to get a list of what countries the library has metadata
   * for.
   */
  public Set<String> getSupportedCountries() {
    return supportedCountries;
  }

  /**
   * Gets a PhoneNumberUtil instance to carry out international phone number formatting, parsing,
   * or validation. The instance is loaded with phone number metadata for a number of most commonly
   * used countries/regions.
   *
   * The PhoneNumberUtil is implemented as a singleton. Therefore, calling getInstance multiple
   * times will only result in one instance being created.
   *
   * @return a PhoneNumberUtil instance
   */
  public static synchronized PhoneNumberUtil getInstance() {
    if (instance == null) {
      instance = new PhoneNumberUtil();
      instance.initializeCountryCodeToRegionCodeMap();
      instance.init(META_DATA_FILE_PREFIX);
    }
    return instance;
  }

  /**
   * Helper function to check region code is not unknown or null.
   */
  private boolean isValidRegionCode(String regionCode) {
    return regionCode != null && supportedCountries.contains(regionCode.toUpperCase());
  }

  /**
   * Formats a phone number in the specified format using default rules. Note that this does not
   * promise to produce a phone number that the user can dial from where they are - although we do
   * format in either 'national' or 'international' format depending on what the client asks for, we
   * do not currently support a more abbreviated format, such as for users in the same "area" who
   * could potentially dial the number without area code. Note that if the phone number has a
   * country code of 0 or an otherwise invalid country code, we cannot work out which formatting
   * rules to apply so we return the national significant number with no formatting applied.
   *
   * @param number         the phone number to be formatted
   * @param numberFormat   the format the phone number should be formatted into
   * @return  the formatted phone number
   */
  public String format(PhoneNumber number, PhoneNumberFormat numberFormat) {
    StringBuffer formattedNumber = new StringBuffer(20);
    format(number, numberFormat, formattedNumber);
    return formattedNumber.toString();
  }

  // Same as format(PhoneNumber, PhoneNumberFormat), but accepts mutable StringBuffer as parameters
  // to decrease object creation when invoked many times.
  public void format(PhoneNumber number, PhoneNumberFormat numberFormat,
                     StringBuffer formattedNumber) {
    // Clear the StringBuffer first.
    formattedNumber.setLength(0);
    int countryCode = number.getCountryCode();
    String nationalSignificantNumber = getNationalSignificantNumber(number);
    if (numberFormat == PhoneNumberFormat.E164) {
      // Early exit for E164 case since no formatting of the national number needs to be applied.
      // Extensions are not formatted.
      formattedNumber.append(nationalSignificantNumber);
      formatNumberByFormat(countryCode, PhoneNumberFormat.E164, formattedNumber);
      return;
    }
    // Note getRegionCodeForCountryCode() is used because formatting information for countries which
    // share a country code is contained by only one country for performance reasons. For example,
    // for NANPA countries it will be contained in the metadata for US.
    String regionCode = getRegionCodeForCountryCode(countryCode);
    if (!isValidRegionCode(regionCode)) {
      formattedNumber.append(nationalSignificantNumber);
      return;
    }

    formattedNumber.append(formatNationalNumber(nationalSignificantNumber,
                                                regionCode, numberFormat));
    maybeGetFormattedExtension(number, regionCode, formattedNumber);
    formatNumberByFormat(countryCode, numberFormat, formattedNumber);
  }

  /**
   * Formats a phone number in the specified format using client-defined formatting rules. Note that
   * if the phone number has a country code of zero or an otherwise invalid country code, we cannot
   * work out things like whether there should be a national prefix applied, or how to format
   * extensions, so we return the national significant number with no formatting applied.
   *
   * @param number                        the phone number to be formatted
   * @param numberFormat                  the format the phone number should be formatted into
   * @param userDefinedFormats            formatting rules specified by clients
   * @return  the formatted phone number
   */
  public String formatByPattern(PhoneNumber number,
                                PhoneNumberFormat numberFormat,
                                List<NumberFormat> userDefinedFormats) {
    int countryCode = number.getCountryCode();
    String nationalSignificantNumber = getNationalSignificantNumber(number);
    // Note getRegionCodeForCountryCode() is used because formatting information for countries which
    // share a country code is contained by only one country for performance reasons. For example,
    // for NANPA countries it will be contained in the metadata for US.
    String regionCode = getRegionCodeForCountryCode(countryCode);
    if (!isValidRegionCode(regionCode)) {
      return nationalSignificantNumber;
    }
    List<NumberFormat> userDefinedFormatsCopy =
        new ArrayList<NumberFormat>(userDefinedFormats.size());
    for (NumberFormat numFormat : userDefinedFormats) {
      String nationalPrefixFormattingRule = numFormat.getNationalPrefixFormattingRule();
      if (nationalPrefixFormattingRule.length() > 0) {
        // Before we do a replacement of the national prefix pattern $NP with the national prefix,
        // we need to copy the rule so that subsequent replacements for different numbers have the
        // appropriate national prefix.
        NumberFormat numFormatCopy = new NumberFormat();
        numFormatCopy.mergeFrom(numFormat);
        String nationalPrefix = getMetadataForRegion(regionCode).getNationalPrefix();
        if (nationalPrefix.length() > 0) {
          // Replace $NP with national prefix and $FG with the first group ($1).
          nationalPrefixFormattingRule =
              NP_PATTERN.matcher(nationalPrefixFormattingRule).replaceFirst(nationalPrefix);
          nationalPrefixFormattingRule =
              FG_PATTERN.matcher(nationalPrefixFormattingRule).replaceFirst("\\$1");
          numFormatCopy.setNationalPrefixFormattingRule(nationalPrefixFormattingRule);
        } else {
          // We don't want to have a rule for how to format the national prefix if there isn't one.
          numFormatCopy.clearNationalPrefixFormattingRule();
        }
        userDefinedFormatsCopy.add(numFormatCopy);
      } else {
        // Otherwise, we just add the original rule to the modified list of formats.
        userDefinedFormatsCopy.add(numFormat);
      }
    }

    StringBuffer formattedNumber =
        new StringBuffer(formatAccordingToFormats(nationalSignificantNumber,
                                                  userDefinedFormatsCopy,
                                                  numberFormat));
    maybeGetFormattedExtension(number, regionCode, formattedNumber);
    formatNumberByFormat(countryCode, numberFormat, formattedNumber);
    return formattedNumber.toString();
  }

  public String formatNationalNumberWithCarrierCode(PhoneNumber number, String carrierCode) {
    int countryCode = number.getCountryCode();
    String nationalSignificantNumber = getNationalSignificantNumber(number);
    // Note getRegionCodeForCountryCode() is used because formatting information for countries which
    // share a country code is contained by only one country for performance reasons. For example,
    // for NANPA countries it will be contained in the metadata for US.
    String regionCode = getRegionCodeForCountryCode(countryCode);
    if (!isValidRegionCode(regionCode)) {
      return nationalSignificantNumber;
    }

    StringBuffer formattedNumber = new StringBuffer(20);
    formattedNumber.append(formatNationalNumber(nationalSignificantNumber,
                                                regionCode,
                                                PhoneNumberFormat.NATIONAL,
                                                carrierCode));
    maybeGetFormattedExtension(number, regionCode, formattedNumber);
    formatNumberByFormat(countryCode, PhoneNumberFormat.NATIONAL, formattedNumber);
    return formattedNumber.toString();
  }

  /**
   * Formats a phone number for out-of-country dialing purpose. If no countryCallingFrom
   * is supplied, we format the number in its INTERNATIONAL format. If the countryCallingFrom is
   * the same as the country where the number is from, then NATIONAL formatting will be applied.
   *
   * If the number itself has a country code of zero or an otherwise invalid country code, then we
   * return the number with no formatting applied.
   *
   * Note this function takes care of the case for calling inside of NANPA and between Russia and
   * Kazakhstan (who share the same country code). In those cases, no international prefix is used.
   * For countries which have multiple international prefixes, the number in its INTERNATIONAL
   * format will be returned instead.
   *
   * @param number               the phone number to be formatted
   * @param countryCallingFrom   the ISO 3166-1 two-letter country code that denotes
   *                             the foreign country where the call is being placed
   * @return  the formatted phone number
   */
  public String formatOutOfCountryCallingNumber(PhoneNumber number,
                                                String countryCallingFrom) {
    if (!isValidRegionCode(countryCallingFrom)) {
      return format(number, PhoneNumberFormat.INTERNATIONAL);
    }
    int countryCode = number.getCountryCode();
    String regionCode = getRegionCodeForCountryCode(countryCode);
    String nationalSignificantNumber = getNationalSignificantNumber(number);
    if (!isValidRegionCode(regionCode)) {
      return nationalSignificantNumber;
    }
    if (countryCode == NANPA_COUNTRY_CODE) {
      if (isNANPACountry(countryCallingFrom)) {
        // For NANPA countries, return the national format for these countries but prefix it with
        // the country code.
        return countryCode + " " + format(number, PhoneNumberFormat.NATIONAL);
      }
    } else if (countryCode == getCountryCodeForRegion(countryCallingFrom)) {
    // For countries that share a country calling code, the country code need not be dialled. This
    // also applies when dialling within a country, so this if clause covers both these cases.
    // Technically this is the case for dialling from la Reunion to other overseas departments of
    // France (French Guiana, Martinique, Guadeloupe), but not vice versa - so we don't cover this
    // edge case for now and for those cases return the version including country code.
    // Details here: http://www.petitfute.com/voyage/225-info-pratiques-reunion
      return format(number, PhoneNumberFormat.NATIONAL);
    }
    String formattedNationalNumber =
        formatNationalNumber(nationalSignificantNumber,
                             regionCode, PhoneNumberFormat.INTERNATIONAL);
    PhoneMetadata metadata = getMetadataForRegion(countryCallingFrom);
    String internationalPrefix = metadata.getInternationalPrefix();

    // For countries that have multiple international prefixes, the international format of the
    // number is returned, unless there is a preferred international prefix.
    String internationalPrefixForFormatting = "";
    if (UNIQUE_INTERNATIONAL_PREFIX.matcher(internationalPrefix).matches()) {
      internationalPrefixForFormatting = internationalPrefix;
    } else if (metadata.hasPreferredInternationalPrefix()) {
      internationalPrefixForFormatting = metadata.getPreferredInternationalPrefix();
    }

    StringBuffer formattedNumber = new StringBuffer(formattedNationalNumber);
    maybeGetFormattedExtension(number, regionCode, formattedNumber);
    if (internationalPrefixForFormatting.length() > 0) {
      formattedNumber.insert(0, " ").insert(0, countryCode).insert(0, " ")
          .insert(0, internationalPrefixForFormatting);
    } else {
      formatNumberByFormat(countryCode,
                           PhoneNumberFormat.INTERNATIONAL,
                           formattedNumber);
    }
    return formattedNumber.toString();
  }

  /**
   * Formats a phone number using the original phone number format that the number is parsed from.
   * The original format is embedded in the country_code_source field of the PhoneNumber object
   * passed in. If such information is missing, the number will be formatted into the NATIONAL
   * format by default.
   *
   * @param number  the PhoneNumber that needs to be formatted in its original number format
   * @param countryCallingFrom  the country whose IDD needs to be prefixed if the original number
   *     has one
   * @return  the formatted phone number in its original number format
   */
  public String formatInOriginalFormat(PhoneNumber number, String countryCallingFrom) {
    if (!number.hasCountryCodeSource()) {
      return format(number, PhoneNumberFormat.NATIONAL);
    }
    switch (number.getCountryCodeSource()) {
      case FROM_NUMBER_WITH_PLUS_SIGN:
        return format(number, PhoneNumberFormat.INTERNATIONAL);
      case FROM_NUMBER_WITH_IDD:
        return formatOutOfCountryCallingNumber(number, countryCallingFrom);
      case FROM_NUMBER_WITHOUT_PLUS_SIGN:
        return format(number, PhoneNumberFormat.INTERNATIONAL).substring(1);
      case FROM_DEFAULT_COUNTRY:
      default:
        return format(number, PhoneNumberFormat.NATIONAL);
    }
  }

  /**
   * Gets the national significant number of the a phone number. Note a national significant number
   * doesn't contain a national prefix or any formatting.
   *
   * @param number  the PhoneNumber object for which the national significant number is needed
   * @return  the national significant number of the PhoneNumber object passed in
   */
  public static String getNationalSignificantNumber(PhoneNumber number) {
    // The leading zero in the national (significant) number of an Italian phone number has a
    // special meaning. Unlike the rest of the world, it indicates the number is a landline
    // number. There have been plans to migrate landline numbers to start with the digit two since
    // December 2000, but it has not yet happened.
    // See http://en.wikipedia.org/wiki/%2B39 for more details.
    // Other countries such as Cote d'Ivoire and Gabon use this for their mobile numbers.
    StringBuffer nationalNumber = new StringBuffer(
        (number.hasItalianLeadingZero() &&
         number.getItalianLeadingZero() &&
         isLeadingZeroCountry(number.getCountryCode()))
        ? "0" : ""
    );
    nationalNumber.append(number.getNationalNumber());
    return nationalNumber.toString();
  }

  /**
   * A helper function that is used by format and formatByPattern.
   */
  private void formatNumberByFormat(int countryCode,
                                    PhoneNumberFormat numberFormat,
                                    StringBuffer formattedNumber) {
    switch (numberFormat) {
      case E164:
        formattedNumber.insert(0, countryCode).insert(0, PLUS_SIGN);
        return;
      case INTERNATIONAL:
        formattedNumber.insert(0, " ").insert(0, countryCode).insert(0, PLUS_SIGN);
        return;
      case NATIONAL:
      default:
        return;
    }
  }

  // Simple wrapper of formatNationalNumber for the common case of no carrier code.
  private String formatNationalNumber(String number,
                                      String regionCode,
                                      PhoneNumberFormat numberFormat) {
    return formatNationalNumber(number, regionCode, numberFormat, null);
  }

  // Note in some countries, the national number can be written in two completely different ways
  // depending on whether it forms part of the NATIONAL format or INTERNATIONAL format. The
  // numberFormat parameter here is used to specify which format to use for those cases. If a
  // carrierCode is specified, this will be inserted into the formatted string to replace $CC.
  private String formatNationalNumber(String number,
                                      String regionCode,
                                      PhoneNumberFormat numberFormat,
                                      String carrierCode) {
    PhoneMetadata metadata = getMetadataForRegion(regionCode);
    List<NumberFormat> intlNumberFormats = metadata.getIntlNumberFormatList();
    // When the intlNumberFormats exists, we use that to format national number for the
    // INTERNATIONAL format instead of using the numberDesc.numberFormats.
    List<NumberFormat> availableFormats =
        (intlNumberFormats.size() == 0 || numberFormat == PhoneNumberFormat.NATIONAL)
        ? metadata.getNumberFormatList()
        : metadata.getIntlNumberFormatList();
    return formatAccordingToFormats(number, availableFormats, numberFormat, carrierCode);
  }

  // Simple wrapper of formatAccordingToFormats for the common case of no carrier code.
  private String formatAccordingToFormats(String nationalNumber,
                                          List<NumberFormat> availableFormats,
                                          PhoneNumberFormat numberFormat) {
    return formatAccordingToFormats(nationalNumber, availableFormats, numberFormat, null);
  }

  // Note that carrierCode is optional - if NULL or an empty string, no carrier code replacement
  // will take place. Carrier code replacement occurs before national prefix replacement.
  private String formatAccordingToFormats(String nationalNumber,
                                          List<NumberFormat> availableFormats,
                                          PhoneNumberFormat numberFormat,
                                          String carrierCode) {
    for (NumberFormat numFormat : availableFormats) {
      int size = numFormat.getLeadingDigitsPatternCount();
      if (size == 0 || regexCache.getPatternForRegex(
              // We always use the last leading_digits_pattern, as it is the most detailed.
              numFormat.getLeadingDigitsPattern(size - 1)).matcher(nationalNumber).lookingAt()) {
        Matcher m = regexCache.getPatternForRegex(numFormat.getPattern()).matcher(nationalNumber);
        String numberFormatRule = numFormat.getFormat();
        if (m.matches()) {
          if (carrierCode != null && carrierCode.length() > 0 &&
              numFormat.getDomesticCarrierCodeFormattingRule().length() > 0) {
            // Replace the $CC in the formatting rule with the desired carrier code.
            String carrierCodeFormattingRule = numFormat.getDomesticCarrierCodeFormattingRule();
            carrierCodeFormattingRule =
                CC_PATTERN.matcher(carrierCodeFormattingRule).replaceFirst(carrierCode);
            // Now replace the $FG in the formatting rule with the first group and the carrier code
            // combined in the appropriate way.
            numberFormatRule = FIRST_GROUP_PATTERN.matcher(numberFormatRule)
                .replaceFirst(carrierCodeFormattingRule);
          }
          String nationalPrefixFormattingRule = numFormat.getNationalPrefixFormattingRule();
          if (numberFormat == PhoneNumberFormat.NATIONAL &&
              nationalPrefixFormattingRule != null &&
              nationalPrefixFormattingRule.length() > 0) {
            Matcher firstGroupMatcher = FIRST_GROUP_PATTERN.matcher(numberFormatRule);
            return m.replaceAll(firstGroupMatcher.replaceFirst(nationalPrefixFormattingRule));
          } else {
            return m.replaceAll(numberFormatRule);
          }
        }
      }
    }

    // If no pattern above is matched, we format the number as a whole.
    return nationalNumber;
  }

  /**
   * Gets a valid number for the specified country.
   *
   * @param regionCode  the ISO 3166-1 two-letter country code that denotes
   *                    the country for which an example number is needed
   * @return  a valid fixed-line number for the specified country. Returns null when the metadata
   *    does not contain such information.
   */
  public PhoneNumber getExampleNumber(String regionCode) {
    return getExampleNumberForType(regionCode, PhoneNumberType.FIXED_LINE);
  }

  /**
   * Gets a valid number for the specified country and number type.
   *
   * @param regionCode  the ISO 3166-1 two-letter country code that denotes
   *                    the country for which an example number is needed
   * @param type  the type of number that is needed
   * @return  a valid number for the specified country and type. Returns null when the metadata
   *     does not contain such information.
   */
  public PhoneNumber getExampleNumberForType(String regionCode, PhoneNumberType type) {
    PhoneNumberDesc desc = getNumberDescByType(getMetadataForRegion(regionCode), type);
    try {
      if (desc.hasExampleNumber()) {
        return parse(desc.getExampleNumber(), regionCode);
      }
    } catch (NumberParseException e) {
      LOGGER.log(Level.SEVERE, e.toString());
    }
    return null;
  }

  /**
   * Appends the formatted extension of a phone number to formattedNumber, if the phone number had
   * an extension specified.
   */
  private void maybeGetFormattedExtension(PhoneNumber number, String regionCode,
                                          StringBuffer formattedNumber) {
    if (number.hasExtension()) {
      // Formats the extension part of the phone number by prefixing it with the appropriate
      // extension prefix. This will be the default extension prefix, unless overridden by a
      // preferred extension prefix for this country.
      PhoneMetadata metadata = getMetadataForRegion(regionCode);
      if (metadata.hasPreferredExtnPrefix()) {
        formattedNumber.append(metadata.getPreferredExtnPrefix());
      } else {
        formattedNumber.append(DEFAULT_EXTN_PREFIX);
      }
      formattedNumber.append(number.getExtension());
    }
  }

  PhoneNumberDesc getNumberDescByType(PhoneMetadata metadata, PhoneNumberType type) {
    switch (type) {
      case PREMIUM_RATE:
        return metadata.getPremiumRate();
      case TOLL_FREE:
        return metadata.getTollFree();
      case MOBILE:
        return metadata.getMobile();
      case FIXED_LINE:
      case FIXED_LINE_OR_MOBILE:
        return metadata.getFixedLine();
      case SHARED_COST:
        return metadata.getSharedCost();
      case VOIP:
        return metadata.getVoip();
      case PERSONAL_NUMBER:
        return metadata.getPersonalNumber();
      default:
        return metadata.getGeneralDesc();
    }
  }

  /**
   * Gets the type of a phone number.
   *
   * @param number  the phone number that we want to know the type
   * @return  the type of the phone number
   */
  public PhoneNumberType getNumberType(PhoneNumber number) {
    String regionCode = getRegionCodeForNumber(number);
    if (!isValidRegionCode(regionCode)) {
      return PhoneNumberType.UNKNOWN;
    }
    String nationalSignificantNumber = getNationalSignificantNumber(number);
    return getNumberTypeHelper(nationalSignificantNumber, getMetadataForRegion(regionCode));
  }

  private PhoneNumberType getNumberTypeHelper(String nationalNumber, PhoneMetadata metadata) {
    PhoneNumberDesc generalNumberDesc = metadata.getGeneralDesc();
    if (!generalNumberDesc.hasNationalNumberPattern() ||
        !isNumberMatchingDesc(nationalNumber, generalNumberDesc)) {
      return PhoneNumberType.UNKNOWN;
    }

    if (isNumberMatchingDesc(nationalNumber, metadata.getPremiumRate())) {
      return PhoneNumberType.PREMIUM_RATE;
    }
    if (isNumberMatchingDesc(nationalNumber, metadata.getTollFree())) {
      return PhoneNumberType.TOLL_FREE;
    }
    if (isNumberMatchingDesc(nationalNumber, metadata.getSharedCost())) {
      return PhoneNumberType.SHARED_COST;
    }
    if (isNumberMatchingDesc(nationalNumber, metadata.getVoip())) {
      return PhoneNumberType.VOIP;
    }
    if (isNumberMatchingDesc(nationalNumber, metadata.getPersonalNumber())) {
      return PhoneNumberType.PERSONAL_NUMBER;
    }

    boolean isFixedLine = isNumberMatchingDesc(nationalNumber, metadata.getFixedLine());
    if (isFixedLine) {
      if (metadata.getSameMobileAndFixedLinePattern()) {
        return PhoneNumberType.FIXED_LINE_OR_MOBILE;
      } else if (isNumberMatchingDesc(nationalNumber, metadata.getMobile())) {
        return PhoneNumberType.FIXED_LINE_OR_MOBILE;
      }
      return PhoneNumberType.FIXED_LINE;
    }
    // Otherwise, test to see if the number is mobile. Only do this if certain that the patterns for
    // mobile and fixed line aren't the same.
    if (!metadata.getSameMobileAndFixedLinePattern() &&
        isNumberMatchingDesc(nationalNumber, metadata.getMobile())) {
      return PhoneNumberType.MOBILE;
    }
    return PhoneNumberType.UNKNOWN;
  }

  PhoneMetadata getMetadataForRegion(String regionCode) {
    if (!isValidRegionCode(regionCode)) {
      return null;
    }
    regionCode = regionCode.toUpperCase();
    if (!countryToMetadataMap.containsKey(regionCode)) {
      loadMetadataForRegionFromFile(currentFilePrefix, regionCode);
    }
    return countryToMetadataMap.get(regionCode);
  }

  private boolean isNumberMatchingDesc(String nationalNumber, PhoneNumberDesc numberDesc) {
    Matcher possibleNumberPatternMatcher =
        regexCache.getPatternForRegex(numberDesc.getPossibleNumberPattern())
            .matcher(nationalNumber);
    Matcher nationalNumberPatternMatcher =
        regexCache.getPatternForRegex(numberDesc.getNationalNumberPattern())
            .matcher(nationalNumber);
    return possibleNumberPatternMatcher.matches() && nationalNumberPatternMatcher.matches();
  }

  /**
   * Tests whether a phone number matches a valid pattern. Note this doesn't verify the number
   * is actually in use, which is impossible to tell by just looking at a number itself.
   *
   * @param number       the phone number that we want to validate
   * @return  a boolean that indicates whether the number is of a valid pattern
   */
  public boolean isValidNumber(PhoneNumber number) {
    String regionCode = getRegionCodeForNumber(number);
    return isValidRegionCode(regionCode)
           && isValidNumberForRegion(number, regionCode);
  }

  /**
   * Tests whether a phone number is valid for a certain region. Note this doesn't verify the number
   * is actually in use, which is impossible to tell by just looking at a number itself. If the
   * country code is not the same as the country code for the region, this immediately exits with
   * false. After this, the specific number pattern rules for the region are examined. This is
   * useful for determining for example whether a particular number is valid for Canada, rather than
   * just a valid NANPA number.
   *
   * @param number       the phone number that we want to validate
   * @param regionCode   the ISO 3166-1 two-letter country code that denotes
   *                     the region/country that we want to validate the phone number for
   * @return  a boolean that indicates whether the number is of a valid pattern
   */
  public boolean isValidNumberForRegion(PhoneNumber number, String regionCode) {
    if (number.getCountryCode() != getCountryCodeForRegion(regionCode)) {
      return false;
    }
    PhoneMetadata metadata = getMetadataForRegion(regionCode);
    PhoneNumberDesc generalNumDesc = metadata.getGeneralDesc();
    String nationalSignificantNumber = getNationalSignificantNumber(number);

    // For countries where we don't have metadata for PhoneNumberDesc, we treat any number passed
    // in as a valid number if its national significant number is between the minimum and maximum
    // lengths defined by ITU for a national significant number.
    if (!generalNumDesc.hasNationalNumberPattern()) {
      int numberLength = nationalSignificantNumber.length();
      return numberLength > MIN_LENGTH_FOR_NSN && numberLength <= MAX_LENGTH_FOR_NSN;
    }
    return getNumberTypeHelper(nationalSignificantNumber, metadata) != PhoneNumberType.UNKNOWN;
  }

  /**
   * Returns the country/region where a phone number is from. This could be used for geo-coding in
   * the country/region level.
   *
   * @param number  the phone number whose origin we want to know
   * @return  the country/region where the phone number is from, or null if no country matches this
   *     calling code.
   */
  public String getRegionCodeForNumber(PhoneNumber number) {
    int countryCode = number.getCountryCode();
    List<String> regions = countryCodeToRegionCodeMap.get(countryCode);
    if (regions == null) {
      return null;
    }
    if (regions.size() == 1) {
      return regions.get(0);
    } else {
      return getRegionCodeForNumberFromRegionList(number, regions);
    }
  }

  private String getRegionCodeForNumberFromRegionList(PhoneNumber number,
                                                      List<String> regionCodes) {
    String nationalNumber = String.valueOf(number.getNationalNumber());
    for (String regionCode : regionCodes) {
      // If leadingDigits is present, use this. Otherwise, do full validation.
      PhoneMetadata metadata = getMetadataForRegion(regionCode);
      if (metadata.hasLeadingDigits()) {
        if (regexCache.getPatternForRegex(metadata.getLeadingDigits())
                .matcher(nationalNumber).lookingAt()) {
          return regionCode;
        }
      } else if (getNumberTypeHelper(nationalNumber, metadata) != PhoneNumberType.UNKNOWN) {
        return regionCode;
      }
    }
    return null;
  }

  /**
   * Returns the region code that matches the specific country code. In the case of no region code
   * being found, ZZ will be returned. In the case of multiple regions, the one designated in the
   * metadata as the "main" country for this calling code will be returned.
   */
  public String getRegionCodeForCountryCode(int countryCode) {
    List<String> regionCodes = countryCodeToRegionCodeMap.get(countryCode);
    return regionCodes == null ? "ZZ" : regionCodes.get(0);
  }

  /**
   * Returns the country calling code for a specific region. For example, this would be 1 for the
   * United States, and 64 for New Zealand.
   *
   * @param regionCode  the ISO 3166-1 two-letter country code that denotes
   *                    the country/region that we want to get the country code for
   * @return  the country calling code for the country/region denoted by regionCode
   */
  public int getCountryCodeForRegion(String regionCode) {
    if (!isValidRegionCode(regionCode)) {
      return 0;
    }
    PhoneMetadata metadata = getMetadataForRegion(regionCode);
    if (metadata == null) {
      return 0;
    }
    return metadata.getCountryCode();
  }

  /**
   * Returns the national dialling prefix for a specific region. For example, this would be 1 for
   * the United States, and 0 for New Zealand. Set stripNonDigits to true to strip symbols like "~"
   * (which indicates a wait for a dialling tone) from the prefix returned. If no national prefix is
   * present, we return null.
   *
   * Warning: Do not use this method for do-your-own formatting - for some countries, the national
   * dialling prefix is used only for certain types of numbers. Use the library's formatting
   * functions to prefix the national prefix when required.
   *
   * @param regionCode  the ISO 3166-1 two-letter country code that denotes
   *                    the country/region that we want to get the dialling prefix for
   * @param stripNonDigits  true to strip non-digits from the national dialling prefix
   * @return  the dialling prefix for the country/region denoted by regionCode
   */
  public String getNddPrefixForRegion(String regionCode, boolean stripNonDigits) {
    if (!isValidRegionCode(regionCode)) {
      LOGGER.log(Level.SEVERE, "Invalid or missing country code provided.");
      return null;
    }
    PhoneMetadata metadata = getMetadataForRegion(regionCode);
    if (metadata == null) {
      LOGGER.log(Level.SEVERE, "Unsupported country code provided.");
      return null;
    }
    String nationalPrefix = metadata.getNationalPrefix();
    // If no national prefix was found, we return null.
    if (nationalPrefix.length() == 0) {
      return null;
    }
    if (stripNonDigits) {
      // Note: if any other non-numeric symbols are ever used in national prefixes, these would have
      // to be removed here as well.
      nationalPrefix = nationalPrefix.replace("~", "");
    }
    return nationalPrefix;
  }

  /**
   * Check if a country is one of the countries under the North American Numbering Plan
   * Administration (NANPA).
   *
   * @return  true if regionCode is one of the countries under NANPA
   */
  public boolean isNANPACountry(String regionCode) {
    return regionCode != null && nanpaCountries.contains(regionCode.toUpperCase());
  }

  /**
   * Check whether countryCode represents the country calling code from a country whose national
   * significant number could contain a leading zero. An example of such a country is Italy.
   */
  public static boolean isLeadingZeroCountry(int countryCode) {
    return LEADING_ZERO_COUNTRIES.contains(countryCode);
  }

  /**
   * Convenience wrapper around isPossibleNumberWithReason. Instead of returning the reason for
   * failure, this method returns a boolean value.
   * @param number  the number that needs to be checked
   * @return  true if the number is possible
   */
  public boolean isPossibleNumber(PhoneNumber number) {
    return isPossibleNumberWithReason(number) == ValidationResult.IS_POSSIBLE;
  }

  /**
   * Check whether a phone number is a possible number. It provides a more lenient check than
   * isValidNumber in the following sense:
   *   1. It only checks the length of phone numbers. In particular, it doesn't check starting
   *      digits of the number.
   *   2. It doesn't attempt to figure out the type of the number, but uses general rules which
   *      applies to all types of phone numbers in a country. Therefore, it is much faster than
   *      isValidNumber.
   *   3. For fixed line numbers, many countries have the concept of area code, which together with
   *      subscriber number constitute the national significant number. It is sometimes okay to dial
   *      the subscriber number only when dialing in the same area. This function will return
   *      true if the subscriber-number-only version is passed in. On the other hand, because
   *      isValidNumber validates using information on both starting digits (for fixed line
   *      numbers, that would most likely be area codes) and length (obviously includes the
   *      length of area codes for fixed line numbers), it will return false for the
   *      subscriber-number-only version.
   *
   * @param number  the number that needs to be checked
   * @return  a ValidationResult object which indicates whether the number is possible
   */
  public ValidationResult isPossibleNumberWithReason(PhoneNumber number) {
    int countryCode = number.getCountryCode();
    // Note: For Russian Fed and NANPA numbers, we just use the rules from the default region (US or
    // Russia) since the getRegionCodeForNumber will not work if the number is possible but not
    // valid. This would need to be revisited if the possible number pattern ever differed between
    // various countries within those plans.
    String regionCode = getRegionCodeForCountryCode(countryCode);
    if (!isValidRegionCode(regionCode)) {
      return ValidationResult.INVALID_COUNTRY_CODE;
    }
    String nationalNumber = getNationalSignificantNumber(number);
    PhoneNumberDesc generalNumDesc = getMetadataForRegion(regionCode).getGeneralDesc();
    // Handling case of numbers with no metadata.
    if (!generalNumDesc.hasNationalNumberPattern()) {
      LOGGER.log(Level.FINER, "Checking if number is possible with incomplete metadata.");
      int numberLength = nationalNumber.length();
      if (numberLength < MIN_LENGTH_FOR_NSN) {
        return ValidationResult.TOO_SHORT;
      } else if (numberLength > MAX_LENGTH_FOR_NSN) {
        return ValidationResult.TOO_LONG;
      } else {
        return ValidationResult.IS_POSSIBLE;
      }
    }
    String possibleNumberPattern = generalNumDesc.getPossibleNumberPattern();
    Matcher m = regexCache.getPatternForRegex(possibleNumberPattern).matcher(nationalNumber);
    if (m.lookingAt()) {
      return (m.end() == nationalNumber.length()) ? ValidationResult.IS_POSSIBLE
                                                  : ValidationResult.TOO_LONG;
    } else {
      return ValidationResult.TOO_SHORT;
    }
  }

  /**
   * Check whether a phone number is a possible number given a number in the form of a string, and
   * the country where the number could be dialed from. It provides a more lenient check than
   * isValidNumber. See isPossibleNumber(PhoneNumber number) for details.
   *
   * This method first parses the number, then invokes isPossibleNumber(PhoneNumber number) with the
   * resultant PhoneNumber object.
   *
   * @param number  the number that needs to be checked, in the form of a string
   * @param countryDialingFrom  the ISO 3166-1 two-letter country code that denotes
   *            the country that we are expecting the number to be dialed from.
   *            Note this is different from the country where the number belongs.
   *            For example, the number +1 650 253 0000 is a number that belongs to US.
   *            When written in this form, it could be dialed from any country.
   *            When it is written as 00 1 650 253 0000, it could be dialed from
   *            any country which has international prefix 00. When it is written as
   *            650 253 0000, it could only be dialed from US, and when written as
   *            253 0000, it could only be dialed from US (Mountain View, CA, to be
   *            more specific).
   * @return  true if the number is possible
   */
  public boolean isPossibleNumber(String number, String countryDialingFrom) {
    try {
      return isPossibleNumber(parse(number, countryDialingFrom));
    } catch (NumberParseException e) {
      return false;
    }
  }

  /**
   * Attempts to extract a valid number from a phone number that is too long to be valid, and resets
   * the PhoneNumber object passed in to that valid version. If no valid number could be extracted,
   * the PhoneNumber object passed in will not be modified.
   * @param number a PhoneNumber object which contains a number that is too long to be valid.
   * @return  true if a valid phone number can be successfully extracted.
   */
  public boolean truncateTooLongNumber(PhoneNumber number) {
    if (isValidNumber(number)) {
      return true;
    }
    PhoneNumber numberCopy = new PhoneNumber();
    numberCopy.mergeFrom(number);
    long nationalNumber = number.getNationalNumber();
    do {
      nationalNumber /= 10;
      numberCopy.setNationalNumber(nationalNumber);
      if (isPossibleNumberWithReason(numberCopy) == ValidationResult.TOO_SHORT ||
          nationalNumber == 0) {
        return false;
      }
    } while (!isValidNumber(numberCopy));
    number.setNationalNumber(nationalNumber);
    return true;
  }

  /**
   * Gets an AsYouTypeFormatter for the specific country. Note this function doesn't attempt to
   * figure out the types of phone number being entered on the fly due to performance reasons.
   * Instead, it tries to apply a standard format to all types of phone numbers. For countries
   * where different types of phone numbers follow different formats, the formatter returned
   * will do no formatting but output exactly what is fed into the inputDigit method.
   *
   * If the type of the phone number being entered is known beforehand, use
   * getAsYouTypeFormatterByType instead.
   *
   * @param regionCode  the ISO 3166-1 two-letter country code that denotes
   *                    the country/region where the phone number is being entered
   * @return  an AsYouTypeFormatter object, which could be used to format phone numbers in the
   *     specific country "as you type"
   */
  public AsYouTypeFormatter getAsYouTypeFormatter(String regionCode) {
    return new AsYouTypeFormatter(regionCode);
  }

  // Extracts country code from fullNumber, returns it and places the remaining number in
  // nationalNumber. It assumes that the leading plus sign or IDD has already been removed. Returns
  // 0 if fullNumber doesn't start with a valid country code, and leaves nationalNumber unmodified.
  int extractCountryCode(StringBuffer fullNumber, StringBuffer nationalNumber) {
    int potentialCountryCode;
    int numberLength = fullNumber.length();
    for (int i = 1; i <= 3 && i <= numberLength; i++) {
      potentialCountryCode = Integer.parseInt(fullNumber.substring(0, i));
      if (countryCodeToRegionCodeMap.containsKey(potentialCountryCode)) {
        nationalNumber.append(fullNumber.substring(i));
        return potentialCountryCode;
      }
    }
    return 0;
  }

  /**
   * Tries to extract a country code from a number. This method will return zero if no country code
   * is considered to be present. Country codes are extracted in the following ways:
   *     - by stripping the international dialing prefix of the country the person is dialing from,
   *       if this is present in the number, and looking at the next digits
   *     - by stripping the '+' sign if present and then looking at the next digits
   *     - by comparing the start of the number and the country code of the default region. If the
   *       number is not considered possible for the numbering plan of the default region initially,
   *       but starts with the country code of this region, validation will be reattempted after
   *       stripping this country code. If this number is considered a possible number, then the
   *       first digits will be considered the country code and removed as such.
   *
   * It will throw a NumberParseException if the number starts with a '+' but the country code
   * supplied after this does not match that of any known country.
   *
   * @param number  non-normalized telephone number that we wish to extract a country
   *     code from - may begin with '+'
   * @param defaultRegionMetadata  metadata about the region this number may be from
   * @param nationalNumber  a string buffer to store the national significant number in, in the case
   *     that a country code was extracted. The number is appended to any existing contents. If no
   *     country code was extracted, this will be left unchanged.
   * @param storeCountryCodeSource  true if the country_code_source field of phoneNumber should be
   *     populated.
   * @param phoneNumber  the PhoneNumber object that needs to be populated with country code and
   *     country code source. Note the country code is always populated, whereas country code source
   *     is only populated when keepCountryCodeSource is true.
   * @return  the country code extracted or 0 if none could be extracted
   */
  int maybeExtractCountryCode(String number, PhoneMetadata defaultRegionMetadata,
                              StringBuffer nationalNumber, boolean storeCountryCodeSource,
                              PhoneNumber phoneNumber)
      throws NumberParseException {
    if (number.length() == 0) {
      return 0;
    }
    StringBuffer fullNumber = new StringBuffer(number);
    // Set the default prefix to be something that will never match.
    String possibleCountryIddPrefix = "NonMatch";
    if (defaultRegionMetadata != null) {
      possibleCountryIddPrefix = defaultRegionMetadata.getInternationalPrefix();
    }

    CountryCodeSource countryCodeSource =
        maybeStripInternationalPrefixAndNormalize(fullNumber, possibleCountryIddPrefix);
    if (storeCountryCodeSource) {
      phoneNumber.setCountryCodeSource(countryCodeSource);
    }
    if (countryCodeSource != CountryCodeSource.FROM_DEFAULT_COUNTRY) {
      if (fullNumber.length() < MIN_LENGTH_FOR_NSN) {
        throw new NumberParseException(NumberParseException.ErrorType.TOO_SHORT_AFTER_IDD,
                                       "Phone number had an IDD, but after this was not "
                                       + "long enough to be a viable phone number.");
      }
      int potentialCountryCode = extractCountryCode(fullNumber, nationalNumber);
      if (potentialCountryCode != 0) {
        phoneNumber.setCountryCode(potentialCountryCode);
        return potentialCountryCode;
      }

      // If this fails, they must be using a strange country code that we don't recognize, or
      // that doesn't exist.
      throw new NumberParseException(NumberParseException.ErrorType.INVALID_COUNTRY_CODE,
                                     "Country code supplied was not recognised.");
    } else if (defaultRegionMetadata != null) {
      // Check to see if the number is valid for the default region already. If not, we check to
      // see if the country code for the default region is present at the start of the number.
      PhoneNumberDesc generalDesc = defaultRegionMetadata.getGeneralDesc();
      Pattern validNumberPattern =
          regexCache.getPatternForRegex(generalDesc.getNationalNumberPattern());
      if (!validNumberPattern.matcher(fullNumber).matches()) {
        int defaultCountryCode = defaultRegionMetadata.getCountryCode();
        String defaultCountryCodeString = String.valueOf(defaultCountryCode);
        String normalizedNumber = fullNumber.toString();
        if (normalizedNumber.startsWith(defaultCountryCodeString)) {
          // If so, strip this, and see if the resultant number is valid.
          StringBuffer potentialNationalNumber =
              new StringBuffer(normalizedNumber.substring(defaultCountryCodeString.length()));
          maybeStripNationalPrefix(
              potentialNationalNumber,
              defaultRegionMetadata.getNationalPrefixForParsing(),
              defaultRegionMetadata.getNationalPrefixTransformRule(),
              validNumberPattern);
          Matcher possibleNumberMatcher =
              regexCache.getPatternForRegex(generalDesc.getPossibleNumberPattern()).matcher(
                  potentialNationalNumber);
          // If the resultant number is either valid, or still too long even with the country code
          // stripped, we consider this a better result and keep the potential national number.
          if (validNumberPattern.matcher(potentialNationalNumber).matches() ||
              (possibleNumberMatcher.lookingAt() &&
               possibleNumberMatcher.end() != potentialNationalNumber.length())) {
            nationalNumber.append(potentialNationalNumber);
            if (storeCountryCodeSource) {
              phoneNumber.setCountryCodeSource(CountryCodeSource.FROM_NUMBER_WITHOUT_PLUS_SIGN);
            }
            phoneNumber.setCountryCode(defaultCountryCode);
            return defaultCountryCode;
          }
        }
      }
    }
    // No country code present.
    phoneNumber.setCountryCode(0);
    return 0;
  }

  /**
   * Strips the IDD from the start of the number if present. Helper function used by
   * maybeStripInternationalPrefixAndNormalize.
   */
  private boolean parsePrefixAsIdd(Pattern iddPattern, StringBuffer number) {
    Matcher m = iddPattern.matcher(number);
    if (m.lookingAt()) {
      int matchEnd = m.end();
      // Only strip this if the first digit after the match is not a 0, since country codes cannot
      // begin with 0.
      Matcher digitMatcher = CAPTURING_DIGIT_PATTERN.matcher(number.substring(matchEnd));
      if (digitMatcher.find()) {
        String normalizedGroup = normalizeHelper(digitMatcher.group(1), DIGIT_MAPPINGS, true);
        if (normalizedGroup.equals("0")) {
          return false;
        }
      }
      number.delete(0, matchEnd);
      return true;
    }
    return false;
  }

  /**
   * Strips any international prefix (such as +, 00, 011) present in the number provided, normalizes
   * the resulting number, and indicates if an international prefix was present.
   *
   * @param number  the non-normalized telephone number that we wish to strip any international
   *     dialing prefix from
   * @param possibleIddPrefix  the international direct dialing prefix from the country we
   *     think this number may be dialed in
   * @return  the corresponding CountryCodeSource if an international dialing prefix could be
   *          removed from the number, otherwise CountryCodeSource.FROM_DEFAULT_COUNTRY if the
   *          number did not seem to be in international format
   */
  CountryCodeSource maybeStripInternationalPrefixAndNormalize(
      StringBuffer number,
      String possibleIddPrefix) {
    if (number.length() == 0) {
      return CountryCodeSource.FROM_DEFAULT_COUNTRY;
    }
    if (number.charAt(0) == PLUS_SIGN) {
      number.deleteCharAt(0);
      // Can now normalize the rest of the number since we've consumed the "+" sign at the start.
      normalize(number);
      return CountryCodeSource.FROM_NUMBER_WITH_PLUS_SIGN;
    }
    // Attempt to parse the first digits as an international prefix.
    Pattern iddPattern = regexCache.getPatternForRegex(possibleIddPrefix);
    if (parsePrefixAsIdd(iddPattern, number)) {
      normalize(number);
      return CountryCodeSource.FROM_NUMBER_WITH_IDD;
    }
    // If still not found, then try and normalize the number and then try again. This shouldn't be
    // done before, since non-numeric characters (+ and ~) may legally be in the international
    // prefix.
    normalize(number);
    return parsePrefixAsIdd(iddPattern, number)
           ? CountryCodeSource.FROM_NUMBER_WITH_IDD
           : CountryCodeSource.FROM_DEFAULT_COUNTRY;
  }

  /**
   * Strips any national prefix (such as 0, 1) present in the number provided.
   *
   * @param number  the normalized telephone number that we wish to strip any national
   *     dialing prefix from
   * @param possibleNationalPrefix  a regex that represents the national direct dialing prefix
   *     from the country we think this number may be dialed in
   * @param transformRule  the string that specifies how number should be transformed according
   *     to the regex specified in possibleNationalPrefix
   * @param nationalNumberRule  a regular expression that specifies what a valid phonenumber from
   *     this region should look like after any national prefix was stripped or transformed
   */
  void maybeStripNationalPrefix(StringBuffer number, String possibleNationalPrefix,
                                String transformRule, Pattern nationalNumberRule) {
    int numberLength = number.length();
    if (numberLength == 0 || possibleNationalPrefix.length() == 0) {
      // Early return for numbers of zero length.
      return;
    }
    // Attempt to parse the first digits as a national prefix.
    Matcher m = regexCache.getPatternForRegex(possibleNationalPrefix).matcher(number);
    if (m.lookingAt()) {
      // m.group(1) == null implies nothing was captured by the capturing groups in
      // possibleNationalPrefix; therefore, no transformation is necessary, and we
      // just remove the national prefix.
      if (transformRule == null || transformRule.length() == 0 || m.group(1) == null) {
        // Check that the resultant number is viable. If not, return.
        Matcher nationalNumber = nationalNumberRule.matcher(number.substring(m.end()));
        if (!nationalNumber.matches()) {
          return;
        }
        number.delete(0, m.end());
      } else {
        // Check that the resultant number is viable. If not, return. Check this by copying the
        // string buffer and making the transformation on the copy first.
        StringBuffer transformedNumber = new StringBuffer(number);
        transformedNumber.replace(0, numberLength, m.replaceFirst(transformRule));
        Matcher nationalNumber = nationalNumberRule.matcher(transformedNumber.toString());
        if (!nationalNumber.matches()) {
          return;
        }
        number.replace(0, number.length(), transformedNumber.toString());
      }
    }
  }

  /**
   * Strips any extension (as in, the part of the number dialled after the call is connected,
   * usually indicated with extn, ext, x or similar) from the end of the number, and returns it.
   *
   * @param number  the non-normalized telephone number that we wish to strip the extension from
   * @return        the phone extension
   */
  String maybeStripExtension(StringBuffer number) {
    Matcher m = EXTN_PATTERN.matcher(number);
    // If we find a potential extension, and the number preceding this is a viable number, we assume
    // it is an extension.
    if (m.find() && isViablePhoneNumber(number.substring(0, m.start()))) {
      // The numbers are captured into groups in the regular expression.
      for (int i = 1, length = m.groupCount(); i <= length; i++) {
        if (m.group(i) != null) {
          // We go through the capturing groups until we find one that captured some digits. If none
          // did, then we will return the empty string.
          String extension = m.group(i);
          number.delete(m.start(), number.length());
          return extension;
        }
      }
    }
    return "";
  }

  /**
   * Parses a string and returns it in proto buffer format. This method will throw a
   * NumberParseException exception if the number is not considered to be a possible number. Note
   * that validation of whether the number is actually a valid number for a particular
   * country/region is not performed. This can be done separately with isValidNumber.
   *
   * @param numberToParse     number that we are attempting to parse. This can contain formatting
   *                          such as +, ( and -, as well as a phone number extension.
   * @param defaultCountry    the ISO 3166-1 two-letter country code that denotes the
   *                          country that we are expecting the number to be from. This is only used
   *                          if the number being parsed is not written in international format.
   *                          The country code for the number in this case would be stored as that
   *                          of the default country supplied. If the number is guaranteed to
   *                          start with a '+' followed by the country code, then "ZZ" or
   *                          null can be supplied.
   * @return                  a phone number proto buffer filled with the parsed number
   * @throws NumberParseException  if the string is not considered to be a viable phone number or if
   *                               no default country was supplied and the number is not in
   *                               international format (does not start with +)
   */
  public PhoneNumber parse(String numberToParse, String defaultCountry)
      throws NumberParseException {
    PhoneNumber phoneNumber = new PhoneNumber();
    parse(numberToParse, defaultCountry, phoneNumber);
    return phoneNumber;
  }

  // Same as parse(String, String), but accepts mutable PhoneNumber as a parameter to
  // decrease object creation when invoked many times.
  public void parse(String numberToParse, String defaultCountry, PhoneNumber phoneNumber)
      throws NumberParseException {
    if (!isValidRegionCode(defaultCountry)) {
      if (numberToParse.charAt(0) != PLUS_SIGN) {
        throw new NumberParseException(NumberParseException.ErrorType.INVALID_COUNTRY_CODE,
                                       "Missing or invalid default country.");
      }
    }
    parseHelper(numberToParse, defaultCountry, false, phoneNumber);
  }

  /**
   * Parses a string and returns it in proto buffer format. This method differs from parse() in that
   * it always populates the raw_input field of the protocol buffer with numberToParse as well as
   * the country_code_source field.
   *
   * @param numberToParse     number that we are attempting to parse. This can contain formatting
   *                          such as +, ( and -, as well as a phone number extension.
   * @param defaultCountry    the ISO 3166-1 two-letter country code that denotes the
   *                          country that we are expecting the number to be from. This is only used
   *                          if the number being parsed is not written in international format.
   *                          The country code for the number in this case would be stored as that
   *                          of the default country supplied.
   * @return                  a phone number proto buffer filled with the parsed number
   * @throws NumberParseException  if the string is not considered to be a viable phone number or if
   *                               no default country was supplied
   */
  public PhoneNumber parseAndKeepRawInput(String numberToParse, String defaultCountry)
      throws NumberParseException {
    PhoneNumber phoneNumber = new PhoneNumber();
    parseAndKeepRawInput(numberToParse, defaultCountry, phoneNumber);
    return phoneNumber;
  }

  // Same as parseAndKeepRawInput(String, String), but accepts mutable PhoneNumber as a parameter to
  // decrease object creation when invoked many times.
  public void parseAndKeepRawInput(String numberToParse, String defaultCountry,
                                   PhoneNumber phoneNumber)
      throws NumberParseException {
    if (!isValidRegionCode(defaultCountry)) {
      if (numberToParse.charAt(0) != PLUS_SIGN) {
        throw new NumberParseException(NumberParseException.ErrorType.INVALID_COUNTRY_CODE,
                                       "Missing or invalid default country.");
      }
    }
    parseHelper(numberToParse, defaultCountry, true, phoneNumber);
  }

  /**
   * Parses a string and fills up the phoneNumber. This method is the same as the public
   * parse() method, with the exception that it allows the default country to be null, for use by
   * isNumberMatch().
   */
  private void parseHelper(String numberToParse, String defaultCountry,
                           Boolean keepRawInput, PhoneNumber phoneNumber)
      throws NumberParseException {
    // Extract a possible number from the string passed in (this strips leading characters that
    // could not be the start of a phone number.)
    String number = extractPossibleNumber(numberToParse);
    if (!isViablePhoneNumber(number)) {
      throw new NumberParseException(NumberParseException.ErrorType.NOT_A_NUMBER,
                                     "The string supplied did not seem to be a phone number.");
    }

    if (keepRawInput) {
      phoneNumber.setRawInput(numberToParse);
    }
    StringBuffer nationalNumber = new StringBuffer(number);
    // Attempt to parse extension first, since it doesn't require country-specific data and we want
    // to have the non-normalised number here.
    String extension = maybeStripExtension(nationalNumber);
    if (extension.length() > 0) {
      phoneNumber.setExtension(extension);
    }

    PhoneMetadata countryMetadata = getMetadataForRegion(defaultCountry);
    // Check to see if the number is given in international format so we know whether this number is
    // from the default country or not.
    StringBuffer normalizedNationalNumber = new StringBuffer();
    int countryCode = maybeExtractCountryCode(nationalNumber.toString(), countryMetadata,
                                              normalizedNationalNumber, keepRawInput, phoneNumber);
    if (countryCode != 0) {
      String phoneNumberRegion = getRegionCodeForCountryCode(countryCode);
      if (!phoneNumberRegion.equals(defaultCountry)) {
        countryMetadata = getMetadataForRegion(phoneNumberRegion);
      }
    } else {
      // If no extracted country code, use the region supplied instead. The national number is just
      // the normalized version of the number we were given to parse.
      normalize(nationalNumber);
      normalizedNationalNumber.append(nationalNumber);
      if (defaultCountry != null) {
        countryCode = countryMetadata.getCountryCode();
        phoneNumber.setCountryCode(countryCode);
      } else if (keepRawInput) {
        phoneNumber.clearCountryCodeSource();
      }
    }
    if (normalizedNationalNumber.length() < MIN_LENGTH_FOR_NSN) {
      throw new NumberParseException(NumberParseException.ErrorType.TOO_SHORT_NSN,
                                     "The string supplied is too short to be a phone number.");
    }
    if (countryMetadata != null) {
      Pattern validNumberPattern =
          regexCache.getPatternForRegex(countryMetadata.getGeneralDesc()
              .getNationalNumberPattern());
      maybeStripNationalPrefix(normalizedNationalNumber,
                               countryMetadata.getNationalPrefixForParsing(),
                               countryMetadata.getNationalPrefixTransformRule(),
                               validNumberPattern);
    }
    int lengthOfNationalNumber = normalizedNationalNumber.length();
    if (lengthOfNationalNumber < MIN_LENGTH_FOR_NSN) {
      throw new NumberParseException(NumberParseException.ErrorType.TOO_SHORT_NSN,
                                     "The string supplied is too short to be a phone number.");
    }
    if (lengthOfNationalNumber > MAX_LENGTH_FOR_NSN) {
      throw new NumberParseException(NumberParseException.ErrorType.TOO_LONG,
                                     "The string supplied is too long to be a phone number.");
    }
    if (normalizedNationalNumber.charAt(0) == '0' &&
        isLeadingZeroCountry(countryCode)) {
      phoneNumber.setItalianLeadingZero(true);
    }
    phoneNumber.setNationalNumber(Long.parseLong(normalizedNationalNumber.toString()));
  }

  /**
   * Takes two phone numbers and compares them for equality.
   *
   * Returns EXACT_MATCH if the country code, NSN, presence of a leading zero for Italian numbers
   * and any extension present are the same.
   * Returns NSN_MATCH if either or both has no country specified, and the NSNs and extensions are
   * the same.
   * Returns SHORT_NSN_MATCH if either or both has no country specified, or the country specified
   * is the same, and one NSN could be a shorter version of the other number. This includes the case
   * where one has an extension specified, and the other does not.
   * Returns NO_MATCH otherwise.
   * For example, the numbers +1 345 657 1234 and 657 1234 are a SHORT_NSN_MATCH.
   * The numbers +1 345 657 1234 and 345 657 are a NO_MATCH.
   *
   * @param firstNumberIn  first number to compare
   * @param secondNumberIn  second number to compare
   *
   * @return  NO_MATCH, SHORT_NSN_MATCH, NSN_MATCH or EXACT_MATCH depending on the level of equality
   *     of the two numbers, described in the method definition.
   */
  public MatchType isNumberMatch(PhoneNumber firstNumberIn, PhoneNumber secondNumberIn) {
    // Make copies of the phone number so that the numbers passed in are not edited.
    PhoneNumber firstNumber = new PhoneNumber();
    firstNumber.mergeFrom(firstNumberIn);
    PhoneNumber secondNumber = new PhoneNumber();
    secondNumber.mergeFrom(secondNumberIn);
    // First clear raw_input and country_code_source field and any empty-string extensions so that
    // we can use the PhoneNumber.exactlySameAs() method.
    firstNumber.clearRawInput();
    firstNumber.clearCountryCodeSource();
    secondNumber.clearRawInput();
    secondNumber.clearCountryCodeSource();
    if (firstNumber.hasExtension() &&
        firstNumber.getExtension().length() == 0) {
        firstNumber.clearExtension();
    }
    if (secondNumber.hasExtension() &&
        secondNumber.getExtension().length() == 0) {
        secondNumber.clearExtension();
    }
    // Early exit if both had extensions and these are different.
    if (firstNumber.hasExtension() && secondNumber.hasExtension() &&
        !firstNumber.getExtension().equals(secondNumber.getExtension())) {
      return MatchType.NO_MATCH;
    }
    int firstNumberCountryCode = firstNumber.getCountryCode();
    int secondNumberCountryCode = secondNumber.getCountryCode();
    // Both had country code specified.
    if (firstNumberCountryCode != 0 && secondNumberCountryCode != 0) {
      if (firstNumber.exactlySameAs(secondNumber)) {
        return MatchType.EXACT_MATCH;
      } else if (firstNumberCountryCode == secondNumberCountryCode &&
                 isNationalNumberSuffixOfTheOther(firstNumber, secondNumber)) {
        // A SHORT_NSN_MATCH occurs if there is a difference because of the presence or absence of
        // an 'Italian leading zero', the presence or absence of an extension, or one NSN being a
        // shorter variant of the other.
        return MatchType.SHORT_NSN_MATCH;
      }
      // This is not a match.
      return MatchType.NO_MATCH;
    }
    // Checks cases where one or both country codes were not specified. To make equality checks
    // easier, we first set the country codes to be equal.
    firstNumber.setCountryCode(secondNumberCountryCode);
    // If all else was the same, then this is an NSN_MATCH.
    if (firstNumber.exactlySameAs(secondNumber)) {
      return MatchType.NSN_MATCH;
    }
    if (isNationalNumberSuffixOfTheOther(firstNumber, secondNumber)) {
      return MatchType.SHORT_NSN_MATCH;
    }
    return MatchType.NO_MATCH;
  }

  // Returns true when one national number is the suffix of the other or both are the same.
  private boolean isNationalNumberSuffixOfTheOther(PhoneNumber firstNumber,
                                                   PhoneNumber secondNumber) {
    String firstNumberNationalNumber = String.valueOf(firstNumber.getNationalNumber());
    String secondNumberNationalNumber = String.valueOf(secondNumber.getNationalNumber());
    // Note that endsWith returns true if the numbers are equal.
    return firstNumberNationalNumber.endsWith(secondNumberNationalNumber) ||
           secondNumberNationalNumber.endsWith(firstNumberNationalNumber);
  }

  /**
   * Takes two phone numbers as strings and compares them for equality. This is a convenience
   * wrapper for isNumberMatch(PhoneNumber firstNumber, PhoneNumber secondNumber). No default region
   * is known.
   *
   * @param firstNumber  first number to compare. Can contain formatting, and can have country code
   *     specified with + at the start.
   * @param secondNumber  second number to compare. Can contain formatting, and can have country
   *     code specified with + at the start.
   * @return  NO_MATCH, SHORT_NSN_MATCH, NSN_MATCH, EXACT_MATCH. See isNumberMatch(PhoneNumber
   *     firstNumber, PhoneNumber secondNumber) for more details.
   * @throws NumberParseException  if either number is not considered to be a viable phone
   *     number
   */
  public MatchType isNumberMatch(String firstNumber, String secondNumber)
      throws NumberParseException {
    PhoneNumber number1 = new PhoneNumber();
    parseHelper(firstNumber, null, false, number1);
    PhoneNumber number2 = new PhoneNumber();
    parseHelper(secondNumber, null, false, number2);
    return isNumberMatch(number1, number2);
  }

  /**
   * Takes two phone numbers and compares them for equality. This is a convenience wrapper for
   * isNumberMatch(PhoneNumber firstNumber, PhoneNumber secondNumber). No default region is known.
   *
   * @param firstNumber  first number to compare in proto buffer format.
   * @param secondNumber  second number to compare. Can contain formatting, and can have country
   *     code specified with + at the start.
   * @return  NO_MATCH, SHORT_NSN_MATCH, NSN_MATCH, EXACT_MATCH. See isNumberMatch(PhoneNumber
   *     firstNumber, PhoneNumber secondNumber) for more details.
   * @throws NumberParseException  if the second number is not considered to be a viable phone
   *     number
   */
  public MatchType isNumberMatch(PhoneNumber firstNumber, String secondNumber)
      throws NumberParseException {
    PhoneNumber number2 = new PhoneNumber();
    parseHelper(secondNumber, null, false, number2);
    return isNumberMatch(firstNumber, number2);
  }
}
