grammar Montuno;
@header {
package montuno;
}

file : END* decls+=top? (END+ decls+=top)* END* EOF ;
top
    : id=IDENT ':' type=term                   #Decl
    | id=binder (':' type=term)? '=' defn=term #Defn
    | '{-#' cmd=IDENT (target=term)? '#-}'   #Pragma
    | term                                     #Expr
    ;
term : lambda (',' tuple+=term)* ;
lambda
    : LAMBDA (rands+=lamBind)+ '.' body=lambda #Lam
    | 'let' IDENT ':' type=term '=' defn=term 'in' body=lambda #LetType
    | 'let' IDENT '=' defn=term 'in' body=lambda #Let
    | (spine+=piBinder)+ ARROW body=lambda     #Pi
    | sigma ARROW body=lambda                  #Fun
    | sigma                                  #LamTerm
    ;
sigma
    : '(' binder ':' type=term ')' TIMES body=term #SgNamed
    | type=app TIMES body=term           #SgAnon
    | app                                #SigmaTerm
    ;
app : proj (args+=arg)* ;
proj
    : proj '.' IDENT #ProjNamed
    | proj '.1'      #ProjFst
    | proj '.2'      #ProjSnd
    | atom           #ProjTerm
    ;
arg
    : '{' (IDENT '=')? term '}' #ArgImpl
    | proj                      #ArgExpl
    ;
piBinder
    : '(' (ids+=binder)+ ':' type=term ')'    #PiExpl
    | '{' (ids+=binder)+ (':' type=term)? '}' #PiImpl
    ;
lamBind
    : binder                   #LamExpl
    | '{' binder '}'           #LamImpl
    | '{' IDENT '=' binder '}' #LamName
    ;
atom
    : '(' term ')'             #Rec
    | IDENT                    #Var
    | '_'                      #Hole
    | ('()' | 'Unit' | 'Type') #Star
    | NAT                      #Nat
    | '[' IDENT '|' FOREIGN? '|' term ']' #Foreign
    ;
binder
    : IDENT #Bind
    | '_' #Irrel
    ;
IDENT : [a-zA-Z] [a-zA-Z0-9'_]*;
NAT : [0-9]+;

END : (SEMICOLON | NEWLINE) NEWLINE*;
fragment SEMICOLON : ';';
fragment NEWLINE : '\r'? '\n' | '\r';
SPACES : [ \t] -> skip;
LINE_COMMENT : '--' (~[\r\n])* -> skip;
BLOCK_COMMENT : '{-'~[#] .*? '-}' -> skip;
LAMBDA : '\\' | 'λ';
ARROW : '->' | '→';
TIMES : '×' | '*';
FOREIGN : [^|]+;
