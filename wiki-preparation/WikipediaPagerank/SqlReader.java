/*
 * Computing Wikipedia's internal PageRanks

 * Copyright (c) 2020 Project Nayuki. (MIT License)
 * https://www.nayuki.io/page/computing-wikipedias-internal-pageranks
 *
 * Adapted by Etalab in the context of the PIAF project
 * https://piaf.etalab.studio
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 * - The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 * - The Software is provided "as is", without warranty of any kind, express or
 *   implied, including but not limited to the warranties of merchantability,
 *   fitness for a particular purpose and noninfringement. In no event shall the
 *   authors or copyright holders be liable for any claim, damages or other
 *   liability, whether in an action of contract, tort or otherwise, arising from,
 *   out of or in connection with the Software or the use or other dealings in the
 *   Software.
 *
 */
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/* 
 * An FSM for parsing lines of SQL statements, particularly to extract data from "INSERT INTO" statements.
 */
final class SqlReader {
	
	/*---- Fields ----*/
	
	private final String matchLinePrefix;
	private final String matchLineSuffix;
	
	private BufferedReader input;
	
	
	/*---- Constructor ----*/
	
	// Constructs an SQL reader over the given line-based text input stream, to extract data from the given table name.
	public SqlReader(BufferedReader in, String tableName) {
		this.input = in;
		matchLinePrefix = "INSERT INTO `" + tableName + "` VALUES ";
		matchLineSuffix = ";";
	}
	
	
	/*---- Methods ----*/
	
	// Returns the next list of data tuples, or null at the end of stream.
	public List<List<Object>> readInsertionTuples() throws IOException {
		while (true) {
			String line = input.readLine();
			if (line == null)
				return null;
			else if (line.equals("") || line.startsWith("--"))  // SQL comment lines
				continue;
			else if (!line.startsWith(matchLinePrefix) || !line.endsWith(matchLineSuffix))  // Other SQL lines
				continue;
			
			// Current line has the form: "INSERT into `tablename` VALUES (...),(...),...,(...);"
			String valuesText = line.substring(matchLinePrefix.length(), line.length() - matchLineSuffix.length());
			return parseTuples(valuesText);
		}
	}
	
	
	public void close() throws IOException {
		input.close();
	}
	
	
	private static List<List<Object>> parseTuples(String text) {
		List<List<Object>> result = new ArrayList<>();
		
		// Finite-state machine (ugly)
		int state = 0;
		List<Object> tuple = new ArrayList<>();
		int tokenStart = -1;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			switch (state) {
				// Outside tuple, expecting '('
				case 0:
					if (c == '(')
						state = 1;
					else
						throw new IllegalArgumentException();
					break;
				
				// Inside tuple, expecting item or close
				case 1:
					if (c >= '0' && c <= '9' || c == '-' || c == '.')
						state = 2;
					else if (c == '\'')
						state = 3;
					else if (c == 'N')
						state = 5;
					else if (c == ')') {
						result.add(tuple);
						tuple = new ArrayList<Object>();
						state = 8;
					} else
						throw new IllegalArgumentException();
					tokenStart = i;
					if (state == 3)
						tokenStart++;
					break;
				
				// Accumulating number
				case 2:
					if (c >= '0' && c <= '9' || c == '-' || c == '.');
					else if (c == ',' || c == ')') {
						String s = text.substring(tokenStart, i);
						tokenStart = -1;
						if (s.indexOf(".") == -1)
							tuple.add(new Integer(s));
						else
							tuple.add(new Double(s));
						if (c == ',')
							state = 7;
						else if (c == ')') {
							result.add(tuple);
							tuple = new ArrayList<Object>();
							state = 8;
						}
					} else
						throw new IllegalArgumentException();
					break;
				
				// Accumulating string
				case 3:
					if (c == '\'') {
						String s = text.substring(tokenStart, i);
						tokenStart = -1;
						if (s.indexOf('\\') != -1)
							s = s.replaceAll("\\\\(.)", "$1");  // Unescape backslashed characters
						else
							s = new String(s);  // For Java below version 7.0 update 6
						tuple.add(s);
						state = 6;
					} else if (c == '\\')
						state = 4;
					break;
				
				// Accumulating string immediately after '\'
				case 4:
					if (c == '\'' || c == '"' || c == '\\')
						state = 3;
					else
						throw new IllegalArgumentException();
					break;
				
				// Accumulating unquoted symbol
				case 5:
					if (c >= 'A' && c <= 'Z');
					else if (c == ',' || c == ')') {
						if (text.substring(tokenStart, i).equals("NULL"))
							tuple.add(null);
						else
							throw new IllegalArgumentException();
						tokenStart = -1;
						if (c == ',')
							state = 7;
						else if (c == ')') {
							result.add(tuple);
							tuple = new ArrayList<Object>();
							state = 8;
						}
					} else
						throw new IllegalArgumentException();
					break;
				
				// Inside tuple, expecting comma or ')'
				case 6:
					if (c == ',')
						state = 7;
					else if (c == ')') {
						result.add(tuple);
						tuple = new ArrayList<Object>();
						state = 8;
					} else
						throw new IllegalArgumentException();
					break;
				
				// Inside tuple, expecting item
				case 7:
					if (c >= '0' && c <= '9' || c == '-' || c == '.')
						state = 2;
					else if (c == '\'')
						state = 3;
					else if (c == 'N')
						state = 5;
					else
						throw new IllegalArgumentException();
					tokenStart = i;
					if (state == 3)
						tokenStart++;
					break;
				
				// Outside, expecting ',' or end
				case 8:
					if (c == ',')
						state = 9;
					else
						throw new IllegalArgumentException();
					break;
				
				// Outside, expecting '('
				case 9:
					if (c == '(')
						state = 1;
					else
						throw new IllegalArgumentException();
					break;
				
				default:
					throw new AssertionError();
			}
		}
		if (state != 8)
			throw new IllegalArgumentException();
		
		return result;
	}
	
}
