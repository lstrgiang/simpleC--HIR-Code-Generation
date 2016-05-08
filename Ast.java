import java.io.*;
import java.util.*;

// **********************************************************************
// Ast class (base class for all other kinds of nodes)
// **********************************************************************
abstract class Ast {
}

class Program extends Ast {

    public Program(DeclList declList) {
        this.declList = declList;
    }
    public String codeGen(){
      int gloVarNum = 0;
      String retCode = "";
      String code = declList.codeGen();
      String strDecl = HIRCompiler.strDeclList();
      gloVarNum = declList.gloVarNum();
      String entryFunc = HIRCompiler.opGenerate(HIRCompiler.ENTRY,
                          "main",String.valueOf(gloVarNum));
      if(strDecl!= null) retCode+=strDecl;
      retCode+=entryFunc+"\n";
      retCode += code;
      return retCode;
    }
    // Semantic checking
    public void check(){
      SymbolTable.Initilize();
      this.declList.check();
      if(!SymbolTable.currentScope().containsKey("main"))
        Errors.semanticError(0,0,"Main function undefined");
      else{
        List symTab = SymbolTable.lookupGlobal("main");
        Iterator it = symTab.iterator();
        while(it.hasNext()){
          SymbolEntry tmp = (SymbolEntry) it.next();
          if(tmp instanceof FuncEntry) return;
        }
        Errors.semanticError(0,0,"Main function undefined");
      }
    }

    private DeclList declList;
}

// **********************************************************************
// Decls
// **********************************************************************
class DeclList extends Ast {

    public DeclList(LinkedList decls) {
        this.decls = decls;
    }
    public List getDecls(){return this.decls;}
    public void check(){
      Iterator<?> it = decls.iterator();
      while(it.hasNext())
        ((Decl) it.next()).check();
    }
    private void addVar(){
      Iterator<?> it = decls.iterator();
      while(it.hasNext()){
        Decl tmp = (Decl) it.next();
        if(tmp instanceof VarDecl){
          int index = 0;
          VarDecl var = (VarDecl) tmp;
          if(SymbolTable.size() > 1){
            for(int i=SymbolTable.size()-1;i>=0;i--){
                List sym = SymbolTable.lookupAtScope(i,var.name().val());
              if(sym != null && sym.size() > 0)
                index+=sym.size();
            }
            var.addVar();
          }
          List symTab = SymbolTable.lookupLocal(var.name().val());
          if(symTab.size() == 1) {
            ((SymbolEntry)symTab.get(0)).setNewOpVar(index,var.name().val());}
          else{
            for(int i=0;i<symTab.size();i++){
              SymbolEntry entry = (SymbolEntry)symTab.get(i);
              if(!entry.isFormals()){
                entry.setNewOpVar(index,var.name().val());
              }
            }
          }
        }
      }
    }
    public int gloVarNum(){
      Set symTab = SymbolTable.currentScope().entrySet();
      Iterator<?> it = symTab.iterator();
      int var = 0;
      while(it.hasNext()){
        Map.Entry entry = (Map.Entry) it.next();
        List symList = (LinkedList) entry.getValue();
        for(int i=0;i<symList.size();i++)
          if(((SymbolEntry)symList.get(i)).isGlobal())
            ++var;
      }
      return var;
    }
    public String codeGen(){
      String code = "";
      addVar();
      Iterator<?> it = decls.iterator();
      while(it.hasNext()){
        Decl tmp = (Decl) it.next();
        if(tmp instanceof FnDecl){
          code += ((FnDecl) tmp).codeGen() + "\n";
        }
      }
      return code;
    }
    public int getVarNum(){
      int varNum=0;
      Iterator<?> it = decls.iterator();
      while(it.hasNext()){
        Decl decl = (Decl) it.next();
        if(decl instanceof VarDecl)
          varNum++;
      }
      return varNum;
    }
    //linked list of kids (Decls)
    protected LinkedList decls;
}
  abstract class Decl extends Ast {
  abstract public void check();
  abstract public Id name();
  abstract public Type type();
}

class VarDecl extends Decl {
    public VarDecl(Type type, Id name) {
        this.type = type;
        this.name = name;
    }
    public void check(){
      List<SymbolEntry> list = SymbolTable.lookupLocal(name.val());
      if(list != null)
        Errors.semanticError(name.getLine(),name.getLine(),
          "Multiple declarations within a single scope");
      else{
        SymbolTable.addDecl(this.name.val(),
            new SymbolEntry(this.type,(SymbolTable.size() == 1) ? true : false));
      }
    }
    public Id name(){return this.name;}
    public Type type(){return this.type;}
    public void addVar(){
      SymbolTable.addDecl(this.name.val(),new SymbolEntry(this.type));
    }
    public void codeGen(){
      int index = 0;
      if(SymbolTable.size() > 1){
        for(int i=SymbolTable.size()-1;i>=0;i--){
            List sym = SymbolTable.lookupAtScope(i,name().val());
          if(sym != null && sym.size() > 0)
            index+=sym.size();
        }
        addVar();
      }
      List symTab = SymbolTable.lookupLocal(name().val());
      if(symTab.size() == 1) {

        ((SymbolEntry)symTab.get(0)).setNewOpVar(index,name().val());}
      else{
        for(int i=0;i<symTab.size();i++){
          SymbolEntry entry = (SymbolEntry)symTab.get(i);
          if(!entry.isFormals()){
            entry.setNewOpVar(index,name().val());
          }
        }
      }

    }
    private Type type;
    private Id name;
}

class FnDecl extends Decl {
    //FnDecl Constructor
    public FnDecl(Type type, Id name, FormalsList formalList, FnBody body) {
        this.type = type;
        this.name = name;
        this.formalList = formalList;
        this.body = body;
    }
    //Semantic Checking
    public void check(){
      this.formalList.check();
      List<SymbolEntry> symList = SymbolTable.lookupLocal(name.val());
      boolean badDecl = false;
      if(symList != null){
          for(int i=0;i < symList.size();i++){
          if(symList.get(i) instanceof FuncEntry){
            FuncEntry fn = (FuncEntry)symList.get(i);
            if(this.isSameFnAs(fn.getFormalListOfFunc()) && !fn.isPreFn()){
              Errors.semanticError(name.getLine(),name.getChar(),
                "Multiple Declarations of A Single Function within A Single Scope");
              badDecl = true;
              break;
            }
          }
        }
      }
      if(!badDecl){
        SymbolTable.addDecl(this.name.val(),new FuncEntry(this.type,
          formalList.size(),this.formalList.getFormal(),this.body.getDeclList(),false));
      }
      SymbolTable.addScope();
      if(this.formalList != null)
        formalList.addToScope();
      boolean isReturn = this.body.check(this.type);
      SymbolTable.removeScope();
      if(!isReturn && !this.type.isVoidType())
        Errors.semanticError(this.name.getLine(),this.name.getChar(),
          "Function need return");
    }
    public boolean isSameFnAs(List formals){
      if(this.formalList.size() != formals.size()){
        return false;
      }
      if(!this.formalList.isSameListAs(formals)){
        return false;
      }

      return true;
    }
    public Id name(){return this.name;}
    public Type type(){return this.type;};
    public String codeGen(){
        HIRCompiler.resetTemp();
        HIRCompiler.resetLabel();
        SymbolTable.addScope();
        formalList.addToScopeWithVarOp();
        String bodyCode = this.body.codeGen(this.formalList.getFormal(),this.name.val());
        int numTemp = HIRCompiler.getTempNum();
        int numVar = body.getVarNum();
        SymbolTable.removeScope();
        return HIRCompiler.funcGenerate(this.name.val(),numVar,numTemp,bodyCode);
    }
    public FormalsList getFormalList(){return this.formalList;}
    private Type type;
    private Id name;
    private FormalsList formalList;
    private FnBody body;
}

class FnPreDecl extends Decl {
    public FnPreDecl(Type type, Id name, FormalsList formalList) {
        this.type = type;
        this.name = name;
        this.formalList = formalList;
    }
    public void check(){
      this.formalList.check();
      List<SymbolEntry> symList = SymbolTable.lookupLocal(name.val());

      SymbolTable.addDecl(this.name.val(),new FuncEntry(this.type,
                        this.formalList.size(),this.formalList.getFormal(),null));
    }
    public boolean isSameFnAs(List formals){
      if(this.formalList.size() != formals.size())
        return false;
      if(!this.formalList.isSameListAs(formals))
        return false;
      return true;
    }
    public Id name(){return this.name;}
    public Type type(){return this.type;};
    public FormalsList getFormalList(){return this.formalList;}
    private Type type;
    private Id name;
    private FormalsList formalList;
}

class FormalsList extends Ast {
    public FormalsList(LinkedList formals) {
        this.formals = formals;
    }
    public void addToScope(){
      for(int i=0;i<this.formals.size();i++){
        SymbolTable.addDecl(((FormalDecl)this.formals.get(i)).name().val(),
          new SymbolEntry(((FormalDecl)this.formals.get(i)).type(),false,true));
      }
    }
    public void addToScopeWithVarOp(){
      this.addToScope();
      for(int i=0;i<this.formals.size();i++){
        int index = 0;
        FormalDecl formal = (FormalDecl)formals.get(i);
        if(SymbolTable.size() > 1){
          for(int j=SymbolTable.size()-1;j>=0;j--){
              List sym = SymbolTable.lookupAtScope(j,formal.name().val());
            if(sym != null && sym.size() > 1)
              index+=sym.size();
          }
        }
        List symTab = SymbolTable.lookupLocal(formal.name().val());
        if(symTab.size() == 1){
          ((SymbolEntry)symTab.get(0)).setNewOpVar(index,formal.name().val());
        }else{
          for(int j=0;j<symTab.size();j++){
            SymbolEntry entry = (SymbolEntry)symTab.get(j);
            if(entry.isFormals() && entry.getOpVar() == null){
              entry.setNewOpVar(index,formal.name().val());
            }
          }
        }
      }
    }
    public boolean isSameListAs(FormalsList list){
      return this.isSameListAs(list.getFormal());
    }
    public boolean isSameListAs(List formals){
      if(this.formals.size() != formals.size())
        return false;
      Iterator<?> it1 = this.formals.iterator();
      Iterator<?> it2 = formals.iterator();
      while(it1.hasNext() && it2.hasNext())
        if(!((FormalDecl) it1.next()).isSameFormalTypeAs((FormalDecl)it2.next())){

          return false;
        }

      return true;
    }
    public LinkedList getFormal(){return this.formals;}
    //check() function check all of the FormalDecl in the List formals
    public void check(){
      Iterator<?> it = formals.iterator();
      while(it.hasNext()){
        FormalDecl tmp = (FormalDecl) it.next();
        if(tmp.instanceNumIn(formals) > 1)
          Errors.semanticError(tmp.name().getLine(),tmp.name().getChar(),
            "Multiple Declarations of A Symbol within a Single Formal Declaration");
      }
    }
    public int size(){return formals.size();}
    // linked list of kids (FormalDecls)

    private LinkedList formals;
}

class FormalDecl extends Decl {
    //Formal Constructor
    public FormalDecl(Type type, Id name) {
        this.type = type;
        this.name = name;
    }
    //isSameFormalTypeAs return true when this.formal has the same type as
    //the given formal's type
    public boolean isSameFormalTypeAs(FormalDecl formal){
      if(!this.type.name().equals(formal.type().name())){
        return false;
      }
      return true;
    }
    public void check(){}
    //isSameFormalNameAs return true when this.formal has the same name as
    //the given formal's name
    public boolean isSameFormalNameAs(FormalDecl formal){
      if(formal.name().val() == null || this.name.val() == null) return false;
      if(!this.name.val().equals(formal.name().val()))
        return false;
      return true;
    }
    public boolean isSameTypeAs(Type type){
      if(!this.type.name().equals(type.name()))
        return false;
      return true;
    }
    public boolean isSameTypeAs(String typeName){
      if(!this.type.name().equals(typeName))
        return false;
      return true;
    }
    //instanceNumIn return the number of instance of the current formal
    //that is in the List formals
    public int instanceNumIn(LinkedList formals){
      Iterator<?> it = formals.iterator();
      int instance = 0;
      while(it.hasNext()){
        FormalDecl tmp = (FormalDecl) it.next();
        if(this.isSameFormalNameAs(tmp) && this.isSameFormalTypeAs(tmp))
          instance++;
      }
      return instance;
    }
    //type() return type of the current formal
    public Type type(){return this.type;}
    //name() return Id name of the current formal
    public Id name(){return this.name;}
    //Private Values
    private Type type;
    private Id name;
}

class FnBody extends Ast {

    public FnBody(DeclList declList, StmtList stmtList) {
        this.declList = declList;
        this.stmtList = stmtList;
    }
    public boolean check(Type returnType){
      this.declList.check();
      return this.stmtList.check(returnType);
    }
    public List getDeclList(){return this.declList.getDecls();}
    public int getVarNum(){return this.declList.getVarNum()+this.stmtList.getVarNum();}
    public String codeGen(List formals,String name){
      Iterator<?> it = this.declList.getDecls().iterator();
      while(it.hasNext()){
        Decl decl = (Decl) it.next();
        if(decl instanceof VarDecl)
          ((VarDecl) decl).codeGen();
      }
      String bodyCode =  this.stmtList.codeGen(name);
      return bodyCode;
    }
    private DeclList declList;
    private StmtList stmtList;
}

class StmtList extends Ast {

    public StmtList(LinkedList stmts) {
        this.stmts = stmts;
    }
    public boolean check(Type returnType){
      Iterator<?> it = this.stmts.iterator();
      boolean isReturn = false;
      while(it.hasNext())
        isReturn = ((Stmt) it.next()).check(returnType) || isReturn;
      return isReturn;
    }
    public int getVarNum(){
      int varNum = 0;
      Iterator<?> it = stmts.iterator();
      while(it.hasNext()){
        Stmt stmt = (Stmt) it.next();
        if(stmt instanceof IfStmt) varNum += ((IfStmt)stmt).getVarNum();
        if(stmt instanceof IfElseStmt) varNum += ((IfElseStmt)stmt).getVarNum();
        if(stmt instanceof WhileStmt) varNum += ((WhileStmt)stmt).getVarNum();
        if(stmt instanceof ForStmt) varNum += ((ForStmt)stmt).getVarNum();
      }
      return varNum;
    }
    public String codeGen(String name){
      Iterator<?> it = stmts.iterator();
      String stmtCode = "";
      while(it.hasNext()){
        Stmt stmt = (Stmt) it.next();
        stmtCode+=stmt.codeGen(name)+"\n";
      }
      return stmtCode;
    }
    // linked list of kids (Stmts)
    private LinkedList stmts;
}

// **********************************************************************
// Types
// **********************************************************************
class Type extends Ast {

    private Type() {}

    public static Type CreateSimpleType(String name)
    {
        Type t = new Type();
        t.name = name;
        t.size = -1;
        t.numPointers = 0;

        return t;
    }
    public static Type CreateArrayType(String name, int size)
    {
        Type t = new Type();
        t.name = name;
        t.size = size;
        t.numPointers = 0;

        return t;
    }
    public static Type CreatePointerType(String name, int numPointers)
    {
        Type t = new Type();
        t.name = name;
        t.size = -1;
        t.numPointers = numPointers;

        return t;
    }
    public static Type CreateArrayPointerType(String name, int size, int numPointers)
    {
        Type t = new Type();
        t.name = name;
        t.size = size;
        t.numPointers = numPointers;

        return t;
    }

    public String name(){return name;}
    public int size(){return this.size;}
    public boolean isVoidType(){return this.name.equals(voidTypeName);}
    public boolean isIntType(){return this.name.equals(intTypeName);}
    public boolean isStringType(){return this.name.equals(stringTypeName);}
    public boolean isErrorType(){return this.name.equals(errorTypeName);}
    public boolean isErrorUndefinedType(){return this.name.equals(errorUndefinedTypeName);}
    public boolean isErrorFnUndefined(){return this.name.equals(errorFnUndefined);}
    public boolean isSameTypeAs(Type _type){return this.name.equals(_type.name());}
    private String name;
    private int size;  // use if this is an array type
    private int numPointers;

    public static final String voidTypeName = "void";
    public static final String intTypeName = "int";
    public static final String stringTypeName = "String";
    public static final String errorTypeName = "error";
    public static final String errorUndefinedTypeName = "errorUndefined";
    public static final String errorFnUndefined="errorFnUndefined";
}

// **********************************************************************
// Stmts
// **********************************************************************
abstract class Stmt extends Ast {
  abstract public boolean check(Type returnType);
  abstract public String codeGen(String name);
}
class IOStmt extends Stmt{
  public IOStmt(LinkedList exps, String type){
    this.exps = exps;
    this.ioType = type;
  }

  public boolean check(Type returnType){
    Iterator<?> it = exps.iterator();
    while(it.hasNext()){
      Exp exp = (Exp) it.next();
      if(exp!= null){
        Type expType = exp.typeCheck();
        if(expType.isErrorUndefinedType())
          Errors.semanticError(exp.getLine(),exp.getChar(),
            "Undefined symbol");
        else if(expType.isVoidType())
              Errors.semanticError(exp.getLine(),exp.getChar(),
              "Use of non-numeric variable");
        else if(expType.isErrorFnUndefined())
              Errors.semanticError(exp.getLine(),exp.getChar(),
                  "Undefined function");
        else if(expType.isStringType() || expType.isIntType())
          continue;
        else Errors.semanticError(exp.getLine(),exp.getChar(),
            "Type Error");
      }
    }
    return false;
  }
  public String codeGen(String name){
    String code = "";
    for(int i=exps.size()-1;i>=0;i--){
      Exp exp = (Exp) exps.get(i);
      String expCode = exp.codeGen();
      String expOp = exp.getVar();
      if(expCode != null ) code += expCode;
      if(this.isInType())
        code+=HIRCompiler.INDENT + HIRCompiler.opGenerate(HIRCompiler.IN,
          expOp,null,null) + "\n";
      else
        code+=HIRCompiler.INDENT + HIRCompiler.opGenerate(HIRCompiler.OUT,
          expOp,null,null)  + "\n";
    }
    return code;
  }


  public boolean isInType(){return this.ioType.equals(CIN);}
  public boolean isOutType(){return this.ioType.equals(COUT);}

  public static String CIN = "cin";
  public static String COUT = "cout";
  private LinkedList exps;
  private String ioType;

}
class AssignStmt extends Stmt {
    public AssignStmt(Exp lhs, Exp exp) {
        this.lhs = lhs;
        this.exp = exp;
    }
    public boolean check(Type returnType){
      Type typeLeft = lhs.typeCheck();
      Type typeExp = exp.typeCheck();
      if(typeLeft.isSameTypeAs(typeExp) && typeLeft.isIntType())
        return false;
      if(lhs instanceof RelationalExp)
        Errors.semanticError(lhs.getLine(),lhs.getChar(),
          "Assignment of relational expression");
      if(exp instanceof RelationalExp)
        Errors.semanticError(exp.getLine(),exp.getChar(),
          "Assignment from relational expression");
      if(lhs instanceof CallExp)
        Errors.semanticError(lhs.getLine(),lhs.getChar(),
          "Invalid function call assignment");
      if(typeLeft.isErrorUndefinedType())
        Errors.semanticError(lhs.getLine(),lhs.getChar(),
          "Undefined symbol");
      else if(typeLeft.isVoidType())
        Errors.semanticError(lhs.getLine(),lhs.getChar(),
        "Use of non-numeric variable");
      else
        Errors.semanticError(lhs.getLine(),lhs.getChar(),
          "Type Error");
      if(typeExp.isErrorUndefinedType())
        Errors.semanticError(exp.getLine(),exp.getChar(),
          "Undefined symbol");
      else if(typeExp.isVoidType())
            Errors.semanticError(exp.getLine(),exp.getChar(),
            "Use of non-numeric variable");
      else if(typeExp.isErrorFnUndefined())
            Errors.semanticError(exp.getLine(),exp.getChar(),
                "Undefined function");
      else Errors.semanticError(exp.getLine(),exp.getChar(),
          "Type Error");
      return false;
    }
    public String codeGen(String name){
      String code = "";
      String lhsCode = lhs.codeGen();
      String expCode = "";
      if(exp instanceof CallExp){
        expCode = ((CallExp)exp).codeGen(lhs.getVar());
        if(lhsCode != null) code+=lhsCode+"\n";
        if(expCode!= null)  code+=expCode;
      }
      else{
        expCode = exp.codeGen();
        String lhsOp = lhs.getVar();
        String expOp = exp.getVar();
        if(lhsCode != null) code+=lhsCode+"\n";
        if(expCode!= null)  code+=expCode+"\n";
        code += HIRCompiler.INDENT + HIRCompiler.opGenerate(HIRCompiler.ASSIGN,
        lhsOp,expOp,null);
      }
      return code;
    }
    private Exp lhs;
    private Exp exp;
}

class IfStmt extends Stmt {
    public IfStmt(Exp exp, DeclList declList, StmtList stmtList) {
        this.exp = exp;
        this.declList = declList;
        this.stmtList = stmtList;
    }
    public boolean check(Type returnType){
      SymbolTable.addScope();
      this.declList.check();
      boolean isReturn = this.stmtList.check(returnType);
      SymbolTable.removeScope();
      Type typeExp = exp.typeCheck();
      if(typeExp.isIntType()) return isReturn;
      if(typeExp.isErrorUndefinedType())
        Errors.semanticError(exp.getLine(),exp.getChar(),
          "Undefined symbol");
      else if(typeExp.isVoidType())
            Errors.semanticError(exp.getLine(),exp.getChar(),
            "Use of non-numeric variable");
      else if(typeExp.isErrorFnUndefined())
            Errors.semanticError(exp.getLine(),exp.getChar(),
                "Undefined function");
      else Errors.semanticError(exp.getLine(),exp.getChar(),
          "Type Error");
      return isReturn;
    }
    public String codeGen(String name){
      SymbolTable.addScope();
      String label = HIRCompiler.newLabel();
      String code = exp.codeGen();
      String expClass = ((Object)exp).getClass().getName();
      String op = null, opVar1 = null, opVar2 = null;
      switch(expClass){
        case "NotEqualsExp":
          op = HIRCompiler.JUMP_EQUAL;
          opVar1 = ((RelationalExp)exp).getOp1();
          opVar2  = ((RelationalExp)exp).getOp2();
          break;
        case "EqualsExp":
          op = HIRCompiler.JUMP_NOTEQUAL;
          opVar1 = ((RelationalExp)exp).getOp1();
          opVar2  = ((RelationalExp)exp).getOp2();
          break;
        case "LessExp":
          op = HIRCompiler.JUMP_LESSTHAN;
          opVar1 = ((RelationalExp)exp).getOp2();
          opVar2  = ((RelationalExp)exp).getOp1();
          break;
        case "LessEqExp":
          op = HIRCompiler.JUMP_LESSTHANEQUAL;
          opVar1 = ((RelationalExp)exp).getOp2();
          opVar2  = ((RelationalExp)exp).getOp1();
          break;
        case "GreaterExp":
          op = HIRCompiler.JUMP_LESSTHANEQUAL;
          opVar1 = ((RelationalExp)exp).getOp1();
          opVar2  = ((RelationalExp)exp).getOp2();
          break;
        case "GreaterEqExp":
          op = HIRCompiler.JUMP_LESSTHAN;
          opVar1 = ((RelationalExp)exp).getOp1();
          opVar2  = ((RelationalExp)exp).getOp2();
          break;
        case "NotExp":
          op = HIRCompiler.JUMP_IFTRUE;
          opVar1 = exp.getVar();
          break;
        default:
          op = HIRCompiler.JUMP_IFTRUE;
          opVar1 = exp.getVar();
          break;
      }
      code+=HIRCompiler.INDENT + HIRCompiler.opGenerate(op,opVar1,opVar2,label) + "\n";
      String declCode = declList.codeGen();
      String stmtCode = stmtList.codeGen(name);
      if(stmtCode!= null)
        code+=stmtCode + HIRCompiler.INDENT + label+":";

      SymbolTable.removeScope();
      return code;
    }

    public int getVarNum(){
      return this.declList.getVarNum() + this.stmtList.getVarNum();
    }
    private Exp exp;

    private DeclList declList;
    private StmtList stmtList;
}

class IfElseStmt extends Stmt {

    public IfElseStmt(Exp exp, DeclList declList1, StmtList stmtList1,
            DeclList declList2, StmtList stmtList2) {
        this.exp = exp;
        this.declList1 = declList1;
        this.stmtList1 = stmtList1;
        this.declList2 = declList2;
        this.stmtList2 = stmtList2;
    }
    public boolean check(Type returnType){
      SymbolTable.addScope();
      this.declList1.check();
      boolean isReturn1 = this.stmtList1.check(returnType);
      SymbolTable.removeScope();
      SymbolTable.addScope();
      this.declList2.check();
      boolean isReturn2 = this.stmtList2.check(returnType);
      SymbolTable.removeScope();
      Type typeExp = exp.typeCheck();
      if(typeExp.isIntType()) return isReturn1 || isReturn2;
      if(typeExp.isErrorUndefinedType())
        Errors.semanticError(exp.getLine(),exp.getChar(),
          "Undefined symbol");
      else if(typeExp.isVoidType())
            Errors.semanticError(exp.getLine(),exp.getChar(),
            "Use of non-numeric variable");
      else if(typeExp.isErrorFnUndefined())
            Errors.semanticError(exp.getLine(),exp.getChar(),
                "Undefined function");
      else Errors.semanticError(exp.getLine(),exp.getChar(),
          "Type Error");
      return isReturn1 || isReturn2;
    }
    public String codeGen(String name){
      SymbolTable.addScope();
      String label1 = HIRCompiler.newLabel();
      String label2 = HIRCompiler.newLabel();
      String code = exp.codeGen();
      String expClass = ((Object)exp).getClass().getName();
      String op = null, opVar1 = null, opVar2 = null;
      switch(expClass){
        case "NotEqualsExp":
          op = HIRCompiler.JUMP_EQUAL;
          opVar1 = ((RelationalExp)exp).getOp1();
          opVar2  = ((RelationalExp)exp).getOp2();
          break;
        case "EqualsExp":
          op = HIRCompiler.JUMP_NOTEQUAL;
          opVar1 = ((RelationalExp)exp).getOp1();
          opVar2  = ((RelationalExp)exp).getOp2();
          break;
        case "LessExp":
          op = HIRCompiler.JUMP_LESSTHAN;
          opVar1 = ((RelationalExp)exp).getOp2();
          opVar2  = ((RelationalExp)exp).getOp1();
          break;
        case "LessEqExp":
          op = HIRCompiler.JUMP_LESSTHANEQUAL;
          opVar1 = ((RelationalExp)exp).getOp2();
          opVar2  = ((RelationalExp)exp).getOp1();
          break;
        case "GreaterExp":
          op = HIRCompiler.JUMP_LESSTHANEQUAL;
          opVar1 = ((RelationalExp)exp).getOp1();
          opVar2  = ((RelationalExp)exp).getOp2();
          break;
        case "GreaterEqExp":
          op = HIRCompiler.JUMP_LESSTHAN;
          opVar1 = ((RelationalExp)exp).getOp1();
          opVar2  = ((RelationalExp)exp).getOp2();
          break;
        case "NotExp":
          op = HIRCompiler.JUMP_IFTRUE;
          opVar1 = exp.getVar();
          break;
        default:
          op = HIRCompiler.JUMP_IFTRUE;
          opVar1 = exp.getVar();
          break;
      }
      code+=HIRCompiler.INDENT + HIRCompiler.opGenerate(op,opVar1,opVar2,label1) + "\n";
      String declCode1 = declList1.codeGen();
      String declCode2 = declList2.codeGen();
      String stmtCode1 = stmtList1.codeGen(name);
      String stmtCode2 = stmtList2.codeGen(name);
      if(stmtCode1!= null)
        code+=stmtCode1 + HIRCompiler.INDENT +"\n";
      code+=HIRCompiler.INDENT + HIRCompiler.opGenerate(HIRCompiler.JUMP,label2,null,null)+"\n";

      if(stmtCode2!=null){
        code+=label1 +": \n";
        code+=stmtCode2 + HIRCompiler.INDENT + label2+":";
      }
      SymbolTable.removeScope();
      return code;
    }
    public int getVarNum(){
      return this.declList1.getVarNum() + this.stmtList1.getVarNum()
      + this.declList2.getVarNum() + this.stmtList2.getVarNum();
    }
    private Exp exp;
    private DeclList declList1;
    private DeclList declList2;
    private StmtList stmtList1;
    private StmtList stmtList2;
}

class WhileStmt extends Stmt {

    public WhileStmt(Exp exp, DeclList declList, StmtList stmtList) {
        this.exp = exp;
        this.declList1 = declList;
        this.stmtList = stmtList;
    }

    public boolean check(Type returnType){
      SymbolTable.addScope();
      this.declList1.check();
      boolean isReturn = this.stmtList.check(returnType);
      SymbolTable.removeScope();
      Type typeExp = exp.typeCheck();
      if(typeExp.isIntType()) return isReturn;
      if(typeExp.isErrorUndefinedType())
        Errors.semanticError(exp.getLine(),exp.getChar(),
          "Undefined symbol");
      else if(typeExp.isVoidType())
            Errors.semanticError(exp.getLine(),exp.getChar(),
            "Use of non-numeric variable");
      else if(typeExp.isErrorFnUndefined())
            Errors.semanticError(exp.getLine(),exp.getChar(),
                "Undefined function");
      else Errors.semanticError(exp.getLine(),exp.getChar(),
          "Type Error");
      return isReturn;
    }
    public String codeGen(String name){
      SymbolTable.addScope();

      String code = exp.codeGen();
      String label1 = HIRCompiler.newLabel();
      String label2 = HIRCompiler.newLabel();
      code+=HIRCompiler.INDENT+ label1 +": \n";
      String expClass = ((Object)exp).getClass().getName();
      String op = null, opVar1 = null, opVar2 = null;

      switch(expClass){
        case "NotEqualsExp":
          op = HIRCompiler.JUMP_EQUAL;
          opVar1 = ((RelationalExp)exp).getOp1();
          opVar2  = ((RelationalExp)exp).getOp2();
          break;
        case "EqualsExp":
          op = HIRCompiler.JUMP_NOTEQUAL;
          opVar1 = ((RelationalExp)exp).getOp1();
          opVar2  = ((RelationalExp)exp).getOp2();
          break;
        case "LessExp":
          op = HIRCompiler.JUMP_LESSTHAN;
          opVar1 = ((RelationalExp)exp).getOp2();
          opVar2  = ((RelationalExp)exp).getOp1();
          break;
        case "LessEqExp":
          op = HIRCompiler.JUMP_LESSTHANEQUAL;
          opVar1 = ((RelationalExp)exp).getOp2();
          opVar2  = ((RelationalExp)exp).getOp1();

          break;
        case "GreaterExp":
          op = HIRCompiler.JUMP_LESSTHANEQUAL;
          opVar1 = ((RelationalExp)exp).getOp1();
          opVar2  = ((RelationalExp)exp).getOp2();
          break;
        case "GreaterEqExp":
          op = HIRCompiler.JUMP_LESSTHAN;
          opVar1 = ((RelationalExp)exp).getOp1();
          opVar2  = ((RelationalExp)exp).getOp2();
          break;
        case "NotExp":
          op = HIRCompiler.JUMP_IFTRUE;
          opVar1 = exp.getVar();
          break;
        default:
          op = HIRCompiler.JUMP_IFTRUE;
          opVar1 = exp.getVar();
          break;
      }
      code+=HIRCompiler.INDENT + HIRCompiler.opGenerate(op,opVar1,opVar2,label2) + "\n";
      String declCode = declList1.codeGen();
      String stmtCode = stmtList.codeGen(name);
      code+=stmtCode+"\n";
      code+=HIRCompiler.INDENT + HIRCompiler.opGenerate(HIRCompiler.JUMP,label1,null,null)+"\n";
      code+=HIRCompiler.INDENT + label2+": \n";
      SymbolTable.removeScope();
      return code;
    }

    public int getVarNum(){
      return this.declList1.getVarNum() + this.stmtList.getVarNum();
    }
    private Exp exp;
    private DeclList declList1;
    private StmtList stmtList;
}

class ForStmt extends Stmt {
    public ForStmt(Stmt init, Exp cond, Stmt incr,
            DeclList declList1, StmtList stmtList) {
        this.init = init;
        this.cond = cond;
        this.incr = incr;
        this.declList1 = declList1;
        this.stmtList = stmtList;
    }
    public boolean check(Type returnType){
      SymbolTable.addScope();
      this.declList1.check();
      boolean isReturn3 = this.stmtList.check(returnType);
      SymbolTable.removeScope();
      boolean isReturn1 = this.init.check(returnType);
      boolean isReturn2 = this.incr.check(returnType);
      Type typeCond = cond.typeCheck();

      if(!(init instanceof AssignStmt))
        Errors.semanticError(-1,-1,
          "Use of non-assign statement");
      else{
        ((AssignStmt)init).check(null);
      }
      if(!(incr instanceof AssignStmt)){
        Errors.semanticError(-1,-1,
          "Use of non-assign statement");
      }
      if(typeCond.isIntType()) return isReturn3 || isReturn1 || isReturn2;
      if(typeCond.isErrorUndefinedType())
        Errors.semanticError(cond.getLine(),cond.getChar(),
          "Undefined symbol");
      else if(typeCond.isVoidType())
            Errors.semanticError(cond.getLine(),cond.getChar(),
            "Use of non-numeric variable");
      else if(typeCond.isErrorFnUndefined())
            Errors.semanticError(cond.getLine(),cond.getChar(),
                "Undefined function");
      else Errors.semanticError(cond.getLine(),cond.getChar(),
          "Type Error");
      return isReturn1 || isReturn2 || isReturn3;
    }
    public String codeGen(String name){
      SymbolTable.addScope();
      String code = "";
      String initCode = ((AssignStmt)init).codeGen(null);
      String label1 = HIRCompiler.newLabel();
      String label2 = HIRCompiler.newLabel();
      if(initCode != null) code+= initCode+"\n";
      code+=HIRCompiler.INDENT+ label1 +": \n";
      code += cond.codeGen();
      String expClass = ((Object)cond).getClass().getName();
      String op = null, opVar1 = null, opVar2 = null;

      switch(expClass){
        case "NotEqualsExp":
          op = HIRCompiler.JUMP_EQUAL;
          opVar1 = ((RelationalExp)cond).getOp1();
          opVar2  = ((RelationalExp)cond).getOp2();
          break;
        case "EqualsExp":
          op = HIRCompiler.JUMP_NOTEQUAL;
          opVar1 = ((RelationalExp)cond).getOp1();
          opVar2  = ((RelationalExp)cond).getOp2();
          break;
        case "LessExp":
          op = HIRCompiler.JUMP_LESSTHAN;
          opVar1 = ((RelationalExp)cond).getOp2();
          opVar2  = ((RelationalExp)cond).getOp1();
          break;
        case "LessEqExp":
          op = HIRCompiler.JUMP_LESSTHANEQUAL;
          opVar1 = ((RelationalExp)cond).getOp2();
          opVar2  = ((RelationalExp)cond).getOp1();
          break;
        case "GreaterExp":
          op = HIRCompiler.JUMP_LESSTHANEQUAL;
          opVar1 = ((RelationalExp)cond).getOp1();
          opVar2  = ((RelationalExp)cond).getOp2();
          break;
        case "GreaterEqExp":
          op = HIRCompiler.JUMP_LESSTHAN;
          opVar1 = ((RelationalExp)cond).getOp1();
          opVar2  = ((RelationalExp)cond).getOp2();
          break;
        case "NotExp":
          op = HIRCompiler.JUMP_IFTRUE;
          opVar1 = cond.getVar();
          break;
        default:
          op = HIRCompiler.JUMP_IFTRUE;
          opVar1 = cond.getVar();
          break;
      }
      code+=HIRCompiler.INDENT + HIRCompiler.opGenerate(op,opVar1,opVar2,label2) + "\n";
      String incrCode = incr.codeGen(name);
      String declCode = declList1.codeGen();
      String stmtCode = stmtList.codeGen(name);
      if(incrCode!= null) code+=incrCode +"\n";
      if(stmtCode!= null) code+=stmtCode+"\n";
      code+=HIRCompiler.INDENT + HIRCompiler.opGenerate(HIRCompiler.JUMP,label1,null,null)+"\n";
      code+=HIRCompiler.INDENT + label2+": \n";
      SymbolTable.removeScope();
      return code;
    }

    public int getVarNum(){
      return this.declList1.getVarNum() + this.stmtList.getVarNum();
    }
    private Stmt init;
    private Exp cond;
    private Stmt incr;
    private DeclList declList1;
    private StmtList stmtList;
}

class CallStmt extends Stmt {
    public boolean check(Type returnType){
      Type callType = callExp.typeCheck();
      if(callType.isIntType()) return false;
      if(callType.isErrorUndefinedType())
        Errors.semanticError(callExp.getLine(),callExp.getChar(),
          "Undefined symbol");
      else if(callType.isVoidType())
        Errors.semanticError(callExp.getLine(),callExp.getChar(),
          "Use of non-numeric variable");
      else if(callType.isErrorFnUndefined())
        Errors.semanticError(callExp.getLine(),callExp.getChar(),
          "Undefined function");
      else Errors.semanticError(callExp.getLine(),callExp.getChar(),
          "Type Error");
      return false;
    }
    public CallStmt(CallExp callExp) {
        this.callExp = callExp;
    }
    public String codeGen(String name){
       return callExp.codeGen()+"\n\t call "+callExp.funcName()+", "+callExp.actualNum();
    }

    private CallExp callExp;
}

class ReturnStmt extends Stmt {
    public ReturnStmt(Exp exp){
      this.exp = exp;
    }
    public String codeGen(String name){
      this.funcName = name;
      if(exp == null) return HIRCompiler.opGenerate(HIRCompiler.RETURNVOID,
        funcName,null,null);
      String expCode =  exp.codeGen();
      String expOp = exp.getVar();
      String code = "";
      if(expCode != null) code +=expCode + "\n";
      code+=HIRCompiler.INDENT + HIRCompiler.opGenerate(HIRCompiler.RETURNVAL,funcName,expOp,null);
      return code;
    }
    public boolean check(Type returnType){
      if(exp != null){
        if(returnType.isVoidType())
          Errors.semanticError(exp.getLine(),exp.getChar(),
              "Do not need return value");
        Type typeExp = exp.typeCheck();
        if(typeExp.isIntType()) return true;
        if(typeExp.isErrorUndefinedType())
          Errors.semanticError(exp.getLine(),exp.getChar(),
            "Undefined symbol");
        else if(typeExp.isErrorFnUndefined())
              Errors.semanticError(exp.getLine(),exp.getChar(),
                  "Undefined function");

        else Errors.semanticError(exp.getLine(),exp.getChar(),
            "Type Error");
      }else{
        if(!returnType.isVoidType())
          Errors.semanticError(-1,-1,
              "Missing return statement");
      }
      return true;
    }
    private String funcName = null;
    private Exp exp; // null for empty return
}

// **********************************************************************
// Exps
// **********************************************************************
abstract class Exp extends Ast {
    public abstract int getLine();
    public abstract int getChar();
    public abstract Type typeCheck();
    public abstract String getVar();
    public abstract String codeGen();
}

abstract class BasicExp extends Exp{
    private int lineNum;
    private int charNum;

    public BasicExp(int lineNum, int charNum){
        this.lineNum = lineNum;
        this.charNum = charNum;
    }

    public int getLine(){
        return lineNum;
    }
    public int getChar(){
        return charNum;
    }
    public String getVar(){
      return null;
    }
    public String codeGen(){
      return null;
    }
}

class IntLit extends BasicExp {

    public IntLit(int lineNum, int charNum, int intVal) {
        super(lineNum, charNum);
        this.intVal = intVal;
    }
    public Type typeCheck(){return Type.CreateSimpleType(Type.intTypeName);}
    private int intVal;
    public String getVar(){return String.valueOf(intVal);}
    public String codeGen(){
      return null;
    }
}

class StringLit extends BasicExp {
    private int index = -1;
    public StringLit(int lineNum, int charNum, String strVal) {
        super(lineNum, charNum);
        this.strVal = strVal;
    }

    public String str() {
        return strVal;
    }
    public Type typeCheck(){return Type.CreateSimpleType(Type.stringTypeName);}
    private String strVal;
    public String getVar(){
      if(!HIRCompiler.isInStrDecl(strVal))
        this.index  = HIRCompiler.addStrDecl(strVal);
      return HIRCompiler.getStrDecl(index);
    }
    public String codeGen(){
      return null;
    }
}

class Id extends BasicExp {

    public Id(int lineNum, int charNum, String strVal) {
        super(lineNum, charNum);
        this.strVal = strVal;
    }
    public String val(){return this.strVal;}
    public Type typeCheck(){
      List<SymbolEntry> symTab = SymbolTable.lookupGlobal(strVal);
      if(symTab == null)
        return Type.CreateSimpleType(Type.errorUndefinedTypeName);
      symTab.get(0).setIsUSed(true);
      return symTab.get(0).getType();
    }
    public String codeGen(){
      return null;
    }
    public String getVar(){
      List symTab = SymbolTable.lookupGlobal(strVal);
      if(symTab == null) {
         return null;
       }
      String tmp =((SymbolEntry)symTab.get(0)).getOpVar();
      return tmp;
    }
    private String strVal;
}

class ArrayExp extends Exp {

    public ArrayExp(Exp lhs, Exp exp) {
        this.lhs = lhs;
        this.exp = exp;
    }

    public int getLine() {
        return lhs.getLine();
    }
    public Type typeCheck(){
      Type typeLeft = lhs.typeCheck();
      Type typeExp = exp.typeCheck();
      if(typeLeft.isSameTypeAs(typeExp))
        return typeLeft;
      else{
        if(typeLeft.isIntType() && !typeExp.isIntType())
          return typeExp;
        if(!typeLeft.isIntType() && typeExp.isIntType())
          return typeLeft;
      }
      return null;
    }
    public int getChar() {
        return lhs.getChar();
    }
    public String getVar(){
      return null;
    }
    public String codeGen(){
        String lhsCode = lhs.codeGen();

        String expCode = exp.codeGen();
        String code = "";
        if(expCode != null)
          code += expCode + "\n";
        if(lhsCode != null)
          code += lhsCode + "\n";
        return code;
    }
    private Exp lhs;
    private Exp exp;
}

class CallExp extends Exp {

    public String funcName(){
      return this.name.val();
    }
    public int actualNum(){
      return this.actualList.expsNum();
    }
    public CallExp(Id name, ActualList actualList) {
        this.name = name;
        this.actualList = actualList;
        this.op = null;
    }

    public CallExp(Id name) {
        this.name = name;
        this.actualList = new ActualList(new LinkedList());
        this.op= null;
    }
    public Type typeCheck(){
      List<?> expsList = actualList.actualExps();
      Iterator<?> itList = expsList.iterator();
      List<Type> expsType = new LinkedList<Type>();
      while(itList.hasNext()){
        Exp exp = (Exp) itList.next();
        Type type = exp.typeCheck();
        expsType.add(type);
        if(type.isIntType())  continue;
        if(type.isVoidType())
          Errors.semanticError(exp.getLine(),exp.getChar(),
              "Use of non-numeric expression");
        else if(type.isErrorUndefinedType())
          Errors.semanticError(exp.getLine(),exp.getChar(),
              "Undefined symbol");
        else
          Errors.semanticError(exp.getLine(),exp.getChar(),
              "Type Error");
      }
      List<SymbolEntry> symTab = SymbolTable.lookupGlobal(this.name.val());
      if(symTab == null || symTab.size ()<= 0)
        return Type.CreateSimpleType(Type.errorFnUndefined);
      else{
        boolean isRefUndefined = false;
        for(int i=0;i<symTab.size();i++){
          if(symTab.get(i) instanceof FuncEntry){
            FuncEntry fn = (FuncEntry)symTab.get(i);
            if(fn.numParams()==this.actualList.expsNum()){
              List<FormalDecl> formals = fn.getFormalListOfFunc();
              boolean isSameFunc = true;
              if(fn.isPreFn()){
                isRefUndefined = true;
                isSameFunc = false;
              }
              else{
                for(int j=0;j<this.actualList.expsNum();j++){
                  if(!formals.get(j).isSameTypeAs(expsType.get(j))){
                      isRefUndefined = false;
                      isSameFunc = false;
                      break;
                  }
                }
              }
              if(isSameFunc) return fn.getReturnType();
            }
          }
        }
        if(isRefUndefined)
          Errors.semanticError(this.name.getLine(),this.name.getChar(),
            "Undefined Reference");
        return Type.CreateSimpleType(Type.errorFnUndefined);
      }
    }
    public String getVar(){
      return this.op;
    }
    public String codeGen(String lhsVar){
      String code ="";
      String actualCode = actualList.codeGen();
      if(actualCode != null) code+=actualCode + "\n";
      List symTab = SymbolTable.lookupGlobal(name.val());
      this.op = lhsVar;
      code+=HIRCompiler.INDENT + HIRCompiler.opGenerate(HIRCompiler.CALLVAL,
      this.op,name.val(),String.valueOf(actualList.expsNum()))+"\n";
      return code;
    }
    public String codeGen(){
      String code ="";
      String actualCode = actualList.codeGen();
      if(actualCode != null) code+=actualCode + "\n";
      List symTab = SymbolTable.lookupGlobal(name.val());
      if(symTab.size() == 1){
          FuncEntry fn = (FuncEntry) symTab.get(0);
          if(fn.getReturnType().isVoidType())
            code+=HIRCompiler.INDENT + HIRCompiler.opGenerate(HIRCompiler.CALLVOID,
            name.val(),String.valueOf(actualList.expsNum()))+"\n";
          else{
            this.op = HIRCompiler.newTempVar();
            code+=HIRCompiler.INDENT + HIRCompiler.opGenerate(HIRCompiler.CALLVAL,
            this.op,name.val(),String.valueOf(actualList.expsNum()))+"\n";
          }
      }else if(symTab.size() > 1){
        for(int i=0;i<symTab.size();i++){
          FuncEntry fn = (FuncEntry) symTab.get(i);
          if(fn.numParams() != actualList.expsNum()) continue;
          if(fn.getReturnType().isVoidType())
            code+=HIRCompiler.INDENT + HIRCompiler.opGenerate(HIRCompiler.CALLVOID,
            name.val(),String.valueOf(actualList.expsNum()))+"\n";
          else{
            this.op = HIRCompiler.newTempVar();
            code+=HIRCompiler.INDENT + HIRCompiler.opGenerate(HIRCompiler.CALLVAL,
            this.op,name.val(),String.valueOf(actualList.expsNum()))+"\n";
          }
          break;
        }
      }
      return code;
    }

    public int getLine() {
        return name.getLine();
    }

    public int getChar() {
        return name.getChar();
    }
    private String op;
    private Id name;
    private ActualList actualList;
}
class ActualList extends Ast {

    public ActualList(LinkedList exps) {
        this.exps = exps;
    }
    public String codeGen(){
      String code = "";
      String arg = "";
      Iterator<?> it = exps.iterator();
      int i=0;
      while(it.hasNext()){
        Exp exp = (Exp)it.next();
        String expCode = exp.codeGen();
        String expOp = exp.getVar();
        if(expCode!= null) code+=expCode+"\n";
        arg+=HIRCompiler.INDENT+HIRCompiler.opGenerate(HIRCompiler.ARG,
          expOp,String.valueOf(i)) + " \n";
        // arg+=HIRCompiler.INDENT + HIRCompiler.opGenerate(HIRCompiler.ARG,expOp,
        //                                     String.valueOf(i))+"\n";
        ++i;
      }
      arg = arg.substring(0,arg.length()-2);
      code+=arg;
      return code;
    }
    public List actualExps(){return this.exps;}
    public int expsNum(){return this.exps.size();}
    // linked list of kids (Exps)
    private LinkedList exps;
}

abstract class UnaryExp extends Exp {
    abstract public String op();
    public String opVar = null;
    public String codeGen(){
      if(exp instanceof IntLit){
      //  if(op() == HIRCompiler.COMP)

      }
      String code = "";
      String expCode = exp.codeGen();
      if(expCode != null)
        code+=expCode + "\n";
      String expOp = exp.getVar();
      this.opVar = HIRCompiler.newTempVar();
      code+=HIRCompiler.INDENT + HIRCompiler.opGenerate(this.op(),opVar,expOp);
      return code;
    }
    public String getVar(){
      return this.opVar;
    }
    public UnaryExp(Exp exp) {
        this.exp = exp;
    }
    public Type typeCheck(){return exp.typeCheck(); }
    public int getLine() {
        return exp.getLine();
    }

    public int getChar() {
        return exp.getChar();
    }

    protected Exp exp;
}

abstract class BinaryExp extends Exp {
    public BinaryExp(Exp exp1, Exp exp2) {
        this.exp1 = exp1;
        this.exp2 = exp2;
    }

    public int getLine() {
        return exp1.getLine();
    }

    public int getChar() {
        return exp1.getChar();
    }
    public abstract String getVar();
    public abstract String codeGen();

    protected Exp exp1;
    protected Exp exp2;
}


// **********************************************************************
// UnaryExps
// **********************************************************************
class UnaryMinusExp extends UnaryExp {
    public String op(){return HIRCompiler.COMP;}
    public UnaryMinusExp(Exp exp) {
        super(exp);
    }
}

class NotExp extends UnaryExp {
    public String op(){return HIRCompiler.NOT;}
    public NotExp(Exp exp) {
        super(exp);
    }
}

class AddrOfExp extends UnaryExp {
    public String op(){return null;}
    public AddrOfExp(Exp exp) {
        super(exp);
    }
}

class DeRefExp extends UnaryExp {
    public String op(){return null;}
    public DeRefExp(Exp exp) {
        super(exp);
    }
}

abstract class RelationalExp extends BinaryExp{
  abstract public String op();
  public String opVar = null;
  public String opVar1 = null;
  public String opVar2 = null;
  public RelationalExp(Exp exp1, Exp exp2){
    super(exp1,exp2);
  }
  public String codeGen(){
    String code = "";
    String codeExp1 = exp1.codeGen();
    String op1 = exp1.getVar();
    String codeExp2 = exp2.codeGen();
    String op2 = exp2.getVar();
    if(codeExp1 != null)
      code+=codeExp1 + "\n";
    if(codeExp2 != null)
      code+=codeExp2 + "\n";
    this.opVar = null;
    this.opVar1 = op1;
    this.opVar2 = op2;
    return code;
  }
  public String getOp1(){
    return this.opVar1;
  }
  public String getOp2(){
    return this.opVar2;
  }
  public String getVar(){
    return this.opVar;
  };

  public Type typeCheck(){
    Type type1 = exp1.typeCheck();
    Type type2 = exp2.typeCheck();
    if(type1.isErrorUndefinedType() || type2.isErrorUndefinedType())
      return Type.CreateSimpleType(Type.errorUndefinedTypeName);
    else if(type1.isSameTypeAs(type2))
      return type1;
    else
      return Type.CreateSimpleType(Type.errorTypeName);

  }
}
abstract class ArithmeticExp extends BinaryExp{
  abstract public String op();
  public String opVar = null;
  public ArithmeticExp(Exp exp1, Exp exp2){
    super(exp1,exp2);
  }
  public String codeGen(){
    String code = "";
    String codeExp1 = exp1.codeGen();
    String op1 = exp1.getVar();
    String codeExp2 = exp2.codeGen();
    String op2 = exp2.getVar();
    if(codeExp1 != null)
      code+=codeExp1 + "\n";
    if(codeExp2 != null)
      code+=codeExp2 + "\n";
    this.opVar = HIRCompiler.newTempVar();
    code+=HIRCompiler.INDENT+HIRCompiler.opGenerate(this.op(),this.opVar,op1,op2);
    return code;
  }
  public String getVar(){
    return this.opVar;
  };
  public Type typeCheck(){
    if(exp2 instanceof RelationalExp || exp1 instanceof RelationalExp)
      return Type.CreateSimpleType(Type.errorTypeName);
    Type type1 = exp1.typeCheck();
    Type type2 = exp2.typeCheck();
    if(type1.isErrorUndefinedType() || type2.isErrorUndefinedType())
      return Type.CreateSimpleType(Type.errorUndefinedTypeName);
    if(type1.isSameTypeAs(type2))
      return type1;
    // else if(type1.isStringType() && type2.isIntType() ||
    // type1.isIntType() && type2.isStringType())
    else
      return Type.CreateSimpleType(Type.errorTypeName);
  }
}


// **********************************************************************
// BinaryExps
// **********************************************************************
class PlusExp extends ArithmeticExp {
    public String op(){
      return HIRCompiler.ADD;
    };
    public PlusExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }
}

class MinusExp extends ArithmeticExp {
    public String op(){ return HIRCompiler.SUB;}
    public MinusExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }
}

class TimesExp extends ArithmeticExp {
    public String op(){return HIRCompiler.MUL;}
    public TimesExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }
}

class DivideExp extends ArithmeticExp {
    public String op(){return HIRCompiler.DIV;}
    public DivideExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }
}

class ModuloExp extends ArithmeticExp {
    public String op(){return HIRCompiler.MOD;}
    public ModuloExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }
}

class AndExp extends ArithmeticExp {
    public String op(){return HIRCompiler.AND;}
    public AndExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }
}

class OrExp extends ArithmeticExp {
    public String op(){return HIRCompiler.OR;}
    public OrExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }
}

class EqualsExp extends RelationalExp {
    public String op(){return HIRCompiler.EQ;}
    public EqualsExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }
}

class NotEqualsExp extends RelationalExp {
    public String op(){return HIRCompiler.NEQ;}
    public NotEqualsExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }
}

class LessExp extends RelationalExp {
    public String op(){return HIRCompiler.LT;}
    public LessExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }
}

class GreaterExp extends RelationalExp {
    public String op(){return HIRCompiler.GT;}
    public GreaterExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }
}

class LessEqExp extends RelationalExp {
    public String op(){return HIRCompiler.LTE;}
    public LessEqExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }
}

class GreaterEqExp extends RelationalExp {
    public String op(){return HIRCompiler.GTE;}
    public GreaterEqExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }
}




class SymbolTable {
  public static void Initilize(){
    list = new LinkedList<HashMap<String,List<SymbolEntry>>>();
    list.add(new HashMap<String,List<SymbolEntry>>());
  }
  public FuncEntry getFnWith(String name, List formals){
    List symTab = lookupGlobal(name);
    if(symTab == null || symTab.size() <=0 ) return null;
    Iterator<?> it = symTab.iterator();
    while(it.hasNext()){
      SymbolEntry sym = (SymbolEntry) it.next();
      if(!(sym instanceof FuncEntry)) continue;
      List funcFormal = ((FuncEntry)sym).getFormalListOfFunc();
      if(funcFormal.size() != formals.size()) continue;
      for(int i=0;i<funcFormal.size();i++){
        if(((FormalDecl)funcFormal.get(i)).isSameFormalTypeAs((FormalDecl)formals.get(i)))
          return (FuncEntry)sym;
      }
    }
    return null;
  }
  public static void addDecl(String name, SymbolEntry sym){
    if(list == null)   Initilize();
      if (name == null || sym == null)
          return;

      if (list.isEmpty())
          return;

      HashMap<String, List<SymbolEntry>> symTab = list.get(0);
      if (symTab.containsKey(name)){
          symTab.get(name).add(sym);
      }
      List<SymbolEntry> addNew = new LinkedList<SymbolEntry>();
      addNew.add(sym);
      symTab.put(name, addNew);
  }
  public static void addScope() {
    if(list == null)   Initilize();
    list.add(0, new HashMap<String, List<SymbolEntry>>());
  }
  public static List lookupAtScope(int pos,String name){
    if(list == null)   Initilize();
    if (list.isEmpty()) return null;
    if (pos >= list.size()) return null;
    return list.get(pos).get(name);
  }
  public static List lookupLocal(String name) {return lookupAtScope(0,name);}
  public static List lookupPreScope(String name){return lookupAtScope(1,name);}
  public static List lookupGlobal(String name) {
      if (list.isEmpty())
          return null;

      for (HashMap<String,List<SymbolEntry>> symTab : list) {
          List<SymbolEntry> sym = symTab.get(name);
          if (sym != null)
              return sym;
      }
      return null;
  }
  public static void removeScope(){
      if (list.isEmpty())
          return;
      list.remove(0);
  }
  //Return the size of the list;
  public static int size() {return list.size();}
  public static HashMap<String,List<SymbolEntry>> currentScope(){return list!= null ? list.get(0) : null;};
  //List of HashMap: Every list is a single scope. The Last one is the gobal
  //scope list.
  private static List<HashMap<String, List<SymbolEntry>>> list;

}

//Entry for the symbol table
//Each entry contains a Type
class SymbolEntry{
  private Type type;
  private String opVar = null;
  private boolean isGlobalVar = false;
  private boolean isUsed = false;
  private boolean isFormals = false;
  public SymbolEntry(Type type){
    this.type = type;
  }
  public SymbolEntry(Type type,boolean isGlobal){
    this.type = type;
    this.isGlobalVar = isGlobal;
  }
  public SymbolEntry(Type type,boolean isGlobal,boolean isFormals){
    this.type = type;
    this.isGlobalVar = isGlobal;
    this.isFormals = isFormals;
  }
  public void setNewOpVar(int index,String name){
    if(isFormals){
      this.opVar = HIRCompiler.FORMAL + String.valueOf(index)+"_"+name;
      return;
    }
    if(isGlobalVar)
      this.opVar = HIRCompiler.GLOBAL + String.valueOf(index)+"_"+name;
    else
      this.opVar = HIRCompiler.LOCAL + String.valueOf(index)+"_"+name;

  }
  public String getOpVar(){return this.opVar;}
  public Type getType(){return this.type;}
  public void setGlobal(boolean isGlobal){this.isGlobalVar = isGlobal;}
  public void setIsUSed(boolean isUsed){this.isUsed = isUsed;}
  public boolean isGlobal(){return this.isGlobalVar;}
  public boolean isFormals(){return this.isFormals;}
  public boolean isUsed(){return this.isUsed;}
}

class FuncEntry extends SymbolEntry{
  private int numParams;
  private List formals = null;
  private List decls = null;
  private boolean isFnPre = true;
  //private HashMap<String,Type> paramList = null;
  public FuncEntry(Type type,int params,List formals,List decls){
    super(type);
    this.numParams = params;
    this.formals = formals;
    this.decls = decls;
  }
  public FuncEntry(Type type,int params,List formals,List decls,boolean isPre){
    super(type);
    this.numParams = params;
    this.formals = formals;
    this.decls = decls;
    this.isFnPre = false;
  }
  public int numParams(){return this.numParams;}
  public Type getReturnType(){return this.getType();}
  public List getFormalListOfFunc(){return this.formals;}
  public List getDeclListOfFunc(){return this.decls;}
  public boolean isPreFn(){return this.isFnPre;}

}
