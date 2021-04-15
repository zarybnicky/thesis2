grammar Montuno;
@header {
package montuno;
}

file : decls+=top ('.' decls+=top)* '.'? EOF;

top
    : id=IDENT ':' type=term                   #Decl
    | id=binder (':' type=term)? '=' defn=term #Defn
    | termAnn=ann? term                        #Expr
    ;
ann : '%elaborate' | '%normalize' | '%parseOnly' ;
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

WS : [ \t\r\n] -> skip;
COMMENT : '--' (~[\r\n])* -> skip;
NCOMMENT : '{-'~[#] .*? '-}' -> skip;
LAMBDA : '\\' | 'λ';
ARROW : '->' | '→';
FOREIGN : [^|]+;
