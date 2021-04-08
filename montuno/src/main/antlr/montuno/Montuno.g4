grammar Montuno;
@header {
package montuno;
}

file : (decls+=top)* EOF;

top
    : id=IDENT ':' type=term '.' #Decl
    | id=binder (':' type=term)? '=' body=term '.' #Defn
    | '%elaborate' term #Elab
    | '%normalize' term #Norm
    ;

term
    : 'let' name=binder ':' type=term '=' tm=term 'in' body=term #Let
    | LAMBDA (args+=binder)* '.' body=term #Lam
    | '(' (dom+=binder)+ ':' kind=term ')' ARROW cod=term #PiExpl
    | '{' (dom+=binder)+ (':' kind=term)? '}' ARROW cod=term #PiImpl
    | (spine+=atom)+ (ARROW rest=term)? #App
    ;
atom
    : '(' rec=term ')' #Rec
    | IDENT #Var
    | '*' #Star
    | 'Nat' #Nat
    | NAT #LitNat
    | lang=IDENT '::' id=IDENT #Foreign
    ;
binder
    : IDENT #Ident
    | '_' #Hole
    ;

IDENT : [a-zA-Z] [a-zA-Z0-9']*;
NAT : [0-9]+;

WS : [ \t\r\n] -> skip;
COMMENT : '--' (~[\r\n])* -> skip;
NCOMMENT : '{-'~[#] .*? '-}' -> skip;
LAMBDA : '\\' | 'λ';
ARROW : '->' | '→';
