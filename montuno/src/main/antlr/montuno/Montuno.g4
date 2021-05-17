grammar Montuno;
@header {
package montuno;
}

file : END* decls+=top? (END+ decls+=top)* END* EOF ;
top
    : id=IDENT ':' type=term                   #Decl
    | id=binder (':' type=term)? '=' defn=term #Defn
    | '{-#' cmd=IDENT target=term? '#-}'       #Pragma
    | term                                     #Expr
    ;
term
    : 'let' id=binder ':' type=term '=' defn=term 'in' body=term #Let
    | LAMBDA (rands+=lamBind)* '.' body=term                     #Lam
    | (spine+=piBind)+ ARROW body=term                           #Pi
    | rator=atom (rands+=arg)* (ARROW body=term)?                #App
    ;
arg
    : '{' (IDENT '=')? term '}' #ArgImpl
    | atom                      #ArgExpl
    | '!'                       #ArgStop
    ;
piBind
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
    | '*'                      #Star
    | NAT                      #Nat
    | '[' IDENT '|' FOREIGN? '|' term ']' #Foreign
    ;
binder
    : IDENT #Bind
    | '_' #Irrel
    ;

IDENT : [a-zA-Z] [a-zA-Z0-9']*;
NAT : [0-9]+;
COMMAND : [A-Z]+;

END : (SEMICOLON | NEWLINE) NEWLINE*;
fragment SEMICOLON : ';';
fragment NEWLINE : '\r'? '\n' | '\r';
SPACES : [ \t] -> skip;
LINE_COMMENT : '--' (~[\r\n])* -> skip;
BLOCK_COMMENT : '{-'~[#] .*? '-}' -> skip;
LAMBDA : '\\' | 'λ';
ARROW : '->' | '→';
FOREIGN : [^|]+;
