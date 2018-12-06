import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;


public class Spreadsheet {
	
	// Member data
	private int rowCount;
	private int columnCount;
	private ArrayList<String> values;
	private int status;
	
	// Status values
	public static final int OK = 0;
	public static final int IS_INVALID_FORMAT = 1;
	public static final int HAS_LACKING_ENTRIES = 2;
	public static final int HAS_EXTRA_ENTRIES = 3;
	public static final int HAS_CIRCULAR_REFERENCE = 4;
	public static final int HAS_INVALID_EXPRESSION = 5;
	public static final int HAS_INVALID_REFERENCE = 6;
	public static final int INTERNAL_ERROR = 7;
	
	public static final String REFERENCE = new String("REF");
	public static final int NO_REF = -1;
	private static final String DOUBLE_PATTERN = "^[+-]?([0-9]+.)?[0-9]+";
	private static final String REFERENCE_PATTERN = "^[A-Z]+[0-9]+";
	private static final String OPERATOR_PATTERN = "[¥+¥-¥/¥*]|[¥+]{2}|[¥-]{2}";
	
	Spreadsheet() {
		this.rowCount = 0;
		this.columnCount = 0;
		values = null;
		this.status = Spreadsheet.OK;
	}
	
	Spreadsheet(String[] input) {
		int i;
		int entryCount;
		
		if ((input.length > 0) && (input[0].split(" ").length == 2)) {
			try {
				this.columnCount = Integer.parseInt(input[0].split(" ")[0]);
				this.rowCount = Integer.parseInt(input[0].split(" ")[1]);

				entryCount = this.rowCount * this.columnCount;
				if (entryCount != (input.length - 1)) {
					this.status = Spreadsheet.IS_INVALID_FORMAT;
				} else {
					this.values = new ArrayList<String>();
					for (i=1; i <= entryCount; i++) {
						this.values.add(input[i]);
					}
				}
			} catch (Exception e) {
				this.status = Spreadsheet.IS_INVALID_FORMAT;
			}

		} else {
			this.status = Spreadsheet.IS_INVALID_FORMAT;
		}
	}
	
	public void resolveAll() {
		this.status = Spreadsheet.OK;
		for (int i=0; i < this.values.size(); i++) {
			String expression = this.values.get(i);
			Double value = resolve(i, expression);
			this.values.set(i, String.format("%5f", value));
			
			if (getStatus() != Spreadsheet.OK) {
				break;
			}
		}
	}
	
	private int getIndex(String reference) {
		int index = 0;
		
		Matcher m = Pattern.compile("[0-9]+").matcher(reference);
		if (m.find()) {
			int pos = m.start();
			String rowVal = reference.substring(0, pos);
			String columnVal = reference.substring(pos);
			
			for (int i=rowVal.length() - 1; i >= 0; i--) {
				index += Math.pow(26, rowVal.length() - 1 - i) *  
						(rowVal.charAt(i) - ('A' - 1)) * this.columnCount;
			}
			index -= this.columnCount;
			index += Integer.parseInt(columnVal) - 1;
		}
		return index;
	}
	
	public double resolve(int index, String expression) {
		double retVal = 0.0;
		
		if (getStatus() != Spreadsheet.OK) {
			// Skip processing if previous errors were encountered
			return retVal;
		}
		
		if (expression.matches(Spreadsheet.DOUBLE_PATTERN)) {
			// Single double value
			retVal =  Double.parseDouble(expression);
		} else if (expression.matches(Spreadsheet.REFERENCE_PATTERN)) {
			// Single reference
			int i = getIndex(expression);

			if (i > (this.values.size() - 1)) {
				// Referenced index is out of bounds
				this.status = Spreadsheet.HAS_INVALID_REFERENCE;
				return retVal;
			}
				
			String reference = this.values.get(i);
			if (reference != Spreadsheet.REFERENCE) {
				if (index != Spreadsheet.NO_REF) {
					this.values.set(index, Spreadsheet.REFERENCE);
				}
				double value = resolve(i, reference);
				if (index != Spreadsheet.NO_REF) {
					// Update current cell with computed value
					this.values.set(index, String.format("%5f", value));
				}
				// Update value of referenced cell with computed value
				this.values.set(i, String.format("%5f", value));
				retVal = value;
			} else {
				// Circular reference detected, unresolved reference found
				this.status = Spreadsheet.HAS_CIRCULAR_REFERENCE;
			}
		} else {
			// Expression
			if (index != Spreadsheet.NO_REF) {
				this.values.set(index, Spreadsheet.REFERENCE);
			}

			String[] tokens = expression.split(" ");
			Stack <Double> stack = new Stack<Double>();
			for (String token : tokens) {
				if (token.matches(Spreadsheet.OPERATOR_PATTERN)) {
					if (stack.size() > 0) {
						Double result = 0.0;
						Double op1 = stack.pop();
						
						switch (token) {
						case "++":
							result = op1 + 1;
							stack.push(result);
							break;
						case "--":
							result = op1 - 1;
							stack.push(result);
							break;
						default:
							if (stack.size() > 0) {
								Double op2 = stack.pop();
								
								switch (token) {
								case "+" :
									result = op1 + op2;
									break;
								case "-" :
									result = op1 - op2;
									break;
								case "/" :
									result = op2 / op1;
									break;
								case "*" :
									result = op1 * op2;
									break;
								}
								stack.push(result);
							} else {
								// Not enough operands to perform operation
								this.status = Spreadsheet.HAS_INVALID_EXPRESSION;
								break;
							}
							break;
						}
					} else {
						// Not enough operands to perform operation
						this.status = Spreadsheet.HAS_INVALID_EXPRESSION;
						break;
					}
					
				} else {
					// Resolve token
					if (!expression.equals(token)) {
						stack.push(Double.valueOf(resolve(Spreadsheet.NO_REF, token)));
					} else {
						// Recursive resolution
						this.status = Spreadsheet.INTERNAL_ERROR;
						return retVal;
					}
				}
			}
			
			if (stack.size() == 1) {
				retVal = stack.pop();
				if (index != Spreadsheet.NO_REF) {
					this.values.set(index, String.format("%5f", retVal));
				}
			} else {
				// Extra operands remain
				this.status = Spreadsheet.HAS_INVALID_EXPRESSION;
			}
		}
		
		return retVal;
	}
	
	public void printAll() {
		System.out.println(String.format("%d %d", this.columnCount, this.rowCount));
		if ((this.status == Spreadsheet.OK) && (this.values != null)) {
			for (String value : this.values) {
				System.out.println(value);
			}
		}
	}
	
	public int getStatus() {
		return this.status;
	}
	
	public static void main(String[] args) {
		BufferedReader br = null;
		Spreadsheet s = null;
		
		try {
			String line = null;
			ArrayList<String> list = new ArrayList<String>();
			
			br = new BufferedReader(new InputStreamReader(System.in));
			while ((line=br.readLine()) != null) {
				list.add(line);
			}
			
			s = new Spreadsheet(list.toArray(new String[list.size()]));
			int status = s.getStatus();

			if (s.getStatus() == Spreadsheet.OK) {
				s.resolveAll();
				status = s.getStatus();
			}
			
			switch (status) {
			case Spreadsheet.OK:
				s.printAll();
				break;
			case Spreadsheet.IS_INVALID_FORMAT:
				System.out.println("Input data is in an invalid format.");
				break;
			case Spreadsheet.HAS_LACKING_ENTRIES:
				System.out.println("Input data has lacking entries.");
				break;
			case Spreadsheet.HAS_EXTRA_ENTRIES:
				System.out.println("Input data has extra entries.");
				break;
			case Spreadsheet.HAS_INVALID_EXPRESSION:
				System.out.println("Input data has an invalid expression.");
				break;
			case Spreadsheet.HAS_CIRCULAR_REFERENCE:
				System.out.println("Input data has circular references.");
				break;
			case Spreadsheet.HAS_INVALID_REFERENCE:
				System.out.println("Input data has an invalid reference.");
				break;
			case Spreadsheet.INTERNAL_ERROR:
				System.out.println("An internal error occurred.");
				break;
			}
	    } catch(IOException e) {
	        System.out.println("Input data is in an invalid format.");
	    } finally {
	    	try {
	    		if (br != null) {
	    			br.close();
	    		}
	    	} catch (IOException e) {
	    	}
	    }
	}
}
