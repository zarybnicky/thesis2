grammar Lambdapi;
@header {
package lambdapi;
}

term
    : 'let' name=binder ':' type=term '=' tm=term 'in' body=term #Let
    | LAMBDA (args+=binder)* '.' body=term #Lam
    | '(' (dom+=binder)+ ':' kind=term ')' ARROW cod=term #Pi
    | (spine+=atom)+ (ARROW rest=term)? #App
    ;
atom
    : '(' rec=term ')' #Rec
    | IDENT #Var
    | '*' #Star
    | 'Nat' #Nat
    | NAT #LitNat
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
