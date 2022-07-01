import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;


public class Main {

	private static int javaFilesSize =0;
	private static int allFilesSize =0;
	//All .java files in the project will be put in this array
	private static File[] javaFiles = new File[10000];
	//All whitelisted files extensions will be put here
	private static File[] allFiles = new File[10000];
	//Classes/methods are the keys, names of file containing them are in the Set
	private static Map<String, Set<String>> classes = new HashMap<String, Set<String>>();
	private static Map<String, Set<String>> methods = new HashMap<String, Set<String>>();

	/**
	 * Whitelisted files extensions to search for used classes/methods. My project is a spring web app with primefaces, so I use some classes in xml, or xhtml, or properties
	 */
	@SuppressWarnings("serial")
	private static List<String> whitelistedEnd = new ArrayList<String>() { {
			add(".java");
			add(".xml");
			add(".properties");
			add(".xhtml");
		}
	};
	
	
	public static void main(String[] args) {
		listUnusedClasses();
		listUnusedMethods();
	}
	
	/**
	 * execution method
	 */
	public static void listUnusedClasses() {
		File folder = new File(new File(".").getAbsolutePath());
	      long start = System.currentTimeMillis();
	    listFiles(folder);
	      long end = System.currentTimeMillis();
	      System.out.println("1 - List files in directory and sub-directories - Elapsed Time in milliseconds: "+ (end-start)); 
	       start = System.currentTimeMillis();
	    listClasses();
	      end = System.currentTimeMillis();
	      System.out.println("2 - List classes of all files - Elapsed Time in milliseconds: "+ (end-start)); 
	    //listMethods();
	      start = System.currentTimeMillis();
		  System.out.println("3 - Can be long - List all files where all classes are used"); 
	    searchClasses(classes);
	      end = System.currentTimeMillis();
	      System.out.println("    Elapsed Time in milliseconds: "+ (end-start)); 
	      System.out.println("4 - Showing all classes used only in their own file (so unused) ");
	    listUnusedClassesOrMethods(classes);
	}
	
	/**
	 * execution method
	 */
	public static void listUnusedMethods() {
		File folder = new File(new File(".").getAbsolutePath());
	      long start = System.currentTimeMillis();
	    listFiles(folder);
	      long end = System.currentTimeMillis();
	      System.out.println("1 - List files in directory and sub-directories - Elapsed Time in milliseconds: "+ (end-start)); 
	       start = System.currentTimeMillis();
	    listMethods();
	      end = System.currentTimeMillis();
	      System.out.println("2 - List methods of all files - Elapsed Time in milliseconds: "+ (end-start)); 
	    //listMethods();
	      start = System.currentTimeMillis();
		  System.out.println("3 - Can be very long - List all files where all methods are used"); 
	    searchMethods(methods);
	      end = System.currentTimeMillis();
	      System.out.println("    Elapsed Time in milliseconds: "+ (end-start)); 
	      System.out.println("4 - Showing all methods used only in their own file (so unused) ");
	    listUnusedClassesOrMethods(methods);
	}
	
	/**
	 * Outputs all methods/classes that are in only 1 file
	 * @param classes 
	 */
	private static void listUnusedClassesOrMethods(Map<String, Set<String>> classes) {
		for(Map.Entry<String, Set<String>> entry:classes.entrySet()) {
			if(entry.getValue().size()<2) System.out.println(entry.getKey());
		}
	}
	
	/**
	 * List all java files and text files in a given folder so we can loop them later
	 * @param folder
	 */
	private static void listFiles(final File folder) {
	    for (final File file : folder.listFiles()) {
	        if (file.isFile()) {
	        	if(file.getName().endsWith(".java")) {
	        		javaFiles[javaFilesSize++] = file;
	        	}
	        	for(String end :whitelistedEnd)
	        		if(file.getName().endsWith(end))
	        				allFiles[allFilesSize++]=file;
	        }
	        else {
	            listFiles(file);
	        }
	    }
	    
	}

	/**
	 * List all classes and their main file
	 */
	@SuppressWarnings("serial")
	private static void listClasses() {
		for(int i = 0; i < javaFilesSize; i++ ) {
			File file = javaFiles[i];
			removeExtension(file.getName());
			classes.put(removeExtension(file.getName()), new HashSet<String>() {{add(file.getName());}});
		}
	}
	
	
	/**
	 * List all methods and their main file
	 */
	@SuppressWarnings("serial")
	private static void listMethods() {
		try {
			for(File file:javaFiles) {
				//ignore target directories
				if(file.getPath().contains("/target/")||file.getPath().contains("\\target\\")) continue;
				Files.lines(file.toPath()).forEach(
					line ->{
						if(line.contains("public ") && line.contains("(")){
						   String lineSpaces[] =  line.split("\\(")[0].split(" ");
						   String functionName = lineSpaces[lineSpaces.length-1];
						   if(functionName.contains("set") || functionName.contains("get") || functionName.contains("is") ) {
							   String []variableName = functionNameToLinkedVariable(functionName);
							   if(!isInFile(new String[] {"!"+functionName, "="+functionName,") "+functionName,"("+functionName,"= "+functionName,"."+functionName,variableName[0],"this."+variableName[1]}, file)  ) {
								   methods.put(functionName, new HashSet<String>() {{add(file.getName());}});
							   }
						   }else {
							   if(!isInFile(new String[] {"!"+functionName, "="+functionName,") "+functionName,"("+functionName,"= "+functionName,"."+functionName},file) ) {
								   methods.put(functionName, new HashSet<String>() {{add(file.getName());}});
							   }
						   }
						}
					}
				);
	
			}
		} catch (IOException | NullPointerException e) {
			return;
		}
	}
	
	/**
	 * Functions like getters, setters and "isSomthing" are used in xhtml/xml controllers. We have to convert their names to see if they are used with another name.
	 * Xhtml, for example, uses "getSomething", but in the file, the text to use it is "something"
	 * @return an array : Example : functionNameToLinkedVariable("getSomething") -> {"something", "Something"}
	 */
	private static String[] functionNameToLinkedVariable(String functionName) {
		   for(int i=0;i<functionName.length();i++){
			   if(Character.isUpperCase(functionName.charAt(i))){
				   functionName=functionName.substring(i);
			     break;
			   }
		   }
		   return new String[] {functionName.substring(0, 1).toLowerCase() + functionName.substring(1),functionName };
	}
	
	/**
	 * Search for all files containing a class name
	 * @param classes
	 */
	private static void searchClasses(Map<String, Set<String>> classes) {
		try {
			for(File file:allFiles) {
				//ignore target directories
				if(file.getPath().contains("/target/")||file.getPath().contains("\\target\\")) continue;
				classes.forEach((k,v)->{
					if(isInFile(new String[] {k.substring(0, 1).toLowerCase() + k.substring(1)+"<", k+"<",k+"\"",k+".",k+" ","new "+k}, file) ) {
						v.add(file.getName());
					}
				});
			}
	    }catch(NullPointerException e) {
	    	return;
	    }
	}
	
	/**
	 * Search for all files containing a method
	 * @param classes
	 */
	private static void searchMethods(Map<String, Set<String>> classes) {
		try {
			for(File file:allFiles) {
				//ignore target directories
				if(file.getPath().contains("/target/")||file.getPath().contains("\\target\\")) continue;
				classes.forEach((k,v)->{
					if(isInFile(new String[] {k+"(","."+k}, file) ) {
						v.add(file.getName());
					}
				});
			}
	    }catch(NullPointerException e) {
	    	return;
	    }
	}
	
	/**
	 * removes a file extension
	 * @param file
	 * @return guess it
	 */
	private static String removeExtension(String file){
		  return file.replaceFirst("[.][^.]+$", "");
	}

	/**
	 * check if an entry of the given String array is in the given file
	 * @param book the given String array
	 * @param file the given File
	 * @return
	 */
	private static boolean isInFile(String[] book, File file) {
		try {
			//this is the very fastest way I found to find if string is in file
			String content = Files.readString(file.toPath(), StandardCharsets.ISO_8859_1);
			for(String detect:book) {
				if(content.contains(detect)) return true;
			}
			return false;
		} catch (IOException e) {
			e.printStackTrace();
		}
	    return false;
	}


}
