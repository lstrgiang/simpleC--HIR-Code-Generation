import java.util.*;
import java.io.*;

public class HIRCompiler{
  public static final String ENTRY = "entry";
  public static final String ADD = "add";
  public static final String SUB = "sub";
  public static final String DIV = "div";
  public static final String MUL = "mult";
  public static final String MOD = "mod";
  public static final String AND = "and";
  public static final String OR = "or";
  public static final String GT = "gt";
  public static final String GTE = "gte";
  public static final String LT = "lt";
  public static final String LTE = "lte";
  public static final String EQ = "eq";
  public static final String NEQ = "neq";
  public static final String COMP = "comp";
  public static final String NOT = "not";
  public static final String JUMP = "jump";
  public static final String JUMP_NOTEQUAL = "jneq";
  public static final String JUMP_EQUAL= "jeq";
  public static final String JUMP_LESSTHAN = "jlt";
  public static final String JUMP_LESSTHANEQUAL = "jlte";
  public static final String JUMP_IFTRUE = "jt";
  public static final String JUMP_IFFALSE = "jf";
  public static final String GLOBAL = "$";
  public static final String LOCAL = "@";
  public static final String TEMP = "&";
  public static final String FORMAL = "%";
  public static final String LABEL = "~";
  public static final String INDENT = "\t";
  public static final String RETURNVOID = "ret";
  public static final String RETURNVAL = "retf";
  public static final String ASSIGN = "move";
  public static final String ARG = "arg";
  public static final String CALLVOID = "call";
  public static final String CALLVAL = "callf";
  public static final String IN = "read";
  public static final String OUT = "write";
  private static int currentLabelNum = 0;
  private static int numStrDecl = 0;
  private static int currentTempIndx=0;
  public static String newLabel(){
    return LABEL + String.valueOf(currentLabelNum++);
  }
  public static String funcGenerate(String name,int numvar, int numtemp,String body){
    return "func " + name+"\n"+"funci "+numvar+", "+numtemp+"\n"+body+"efunc "+name;
  }
  public static String entryGenerate(String name,int n){
    return "entry "+name+", "+n;
  }
  public static String opGenerate(String op,String result, String op1,String op2){
    if(op1 == null && op2 == null) return op+" "+result;
    if(op1 == null) return op+" "+result+", "+op2;
    if(op2 == null) return op+" "+result+", "+op1;
    return op + " "+result+", "+op1+", "+op2;
  }
  public static String opGenerate(String op,String result,String op2){
    return opGenerate(op,result,op2,null);
  }
  public static String strConstGenerate(String content){
    return "str"+" \"" + content + "\"";
  }

  public static void compile(Program p){
    p.check();
    if(Errors.semanticErrors >= 1){
      System.out.println("Cannot compile");
      return;
    }
    String outName = "output";
    String hirCode = p.codeGen();
    try{
      File file = new File(outName + ".hir");
      if (!file.exists()) {
            file.createNewFile();
      }
      FileWriter fw = new FileWriter(file.getAbsoluteFile());
      BufferedWriter bw = new BufferedWriter(fw);
      bw.write(hirCode);
      System.out.println("HIR code generated in out.hir");
      bw.close();
    }catch(IOException e){
      e.printStackTrace();
    }
  }
  public static String newTempVar(){
    return TEMP + String.valueOf(currentTempIndx++);
  }

  //StrDecl proccessing function
  public static int addStrDecl(String val){
    int index = numStrDecl;
    if(strDecl == null) strDecl = new LinkedList<List<String>>();
    List tmp = new LinkedList<String>();
    String var = "?"+numStrDecl;
    numStrDecl++;
    tmp.add(var);
    tmp.add(val);
    strDecl.add(tmp);
    return index;
  }
  public static boolean isInStrDecl(String val){
    if(strDecl == null) return false;
    Iterator<?> it = strDecl.iterator();
    while(it.hasNext()){
      List tmp = (LinkedList) it.next();
      if(tmp.get(1).equals(val))
        return true;
    }
    return false;
  }
  public static String getStrDecl(int index){
    return (String)((List)strDecl.get(index)).get(0);
  }
  public static String getStrDecl(String val){
    if(strDecl == null) return null;
    Iterator<?> it = strDecl.iterator();
    while(it.hasNext()){
      List tmp = (LinkedList) it.next();
      if(tmp.get(1).equals(val))
        return (String)tmp.get(0);
    }
    return null;
  }
  public static String strDeclList(){
    if(strDecl == null || strDecl.size() < 1) return null;
    String decl = "";
    Iterator<?> it = strDecl.iterator();
    while(it.hasNext()){
      List tmp = (LinkedList) it.next();
      decl += "str "+tmp.get(1)+"\n";
    }
    return decl;
  }
  public static void resetLabel(){currentLabelNum = 0;}
  public static int getTempNum(){return currentTempIndx;}
  public static void resetTemp(){currentTempIndx = 0;}
  private static List strDecl = null;
}
