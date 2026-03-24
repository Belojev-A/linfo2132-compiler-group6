package compiler.Parser;

import compiler.Lexer.Lexer;
import compiler.Lexer.Symbol;
import compiler.Lexer.TokenType;
import compiler.Parser.ast.ASTNode;
import compiler.Parser.ast.LeafNode;

public class Parser {

    private final Lexer lexer;
    private Symbol lookahead;

    public Parser(Lexer lexer) {
        this.lexer = lexer;
        advance();
    }

    private void advance() {
        lookahead = lexer.getNextSymbol();
    }

    private Symbol match(TokenType expected) {
        if (lookahead.type != expected) {
            throw new ParseException(
                "Expected " + expected + " but got " + lookahead.type
                + (lookahead.value != null ? " '" + lookahead.value + "'" : ""),
                lookahead.line
            );
        }
        Symbol matched = lookahead;
        advance();
        return matched;
    }

    private boolean check(TokenType type) {
        return lookahead.type == type;
    }

    private boolean isType() {
        return check(TokenType.INT)    ||
               check(TokenType.FLOAT)  ||
               check(TokenType.BOOL)   ||
               check(TokenType.STRING) ||
               check(TokenType.COLLECTION_NAME) ||
               check(TokenType.ARRAY);
    }

    public ASTNode getAST() {
        ASTNode root = parseProgram();
        match(TokenType.EOF);
        return root;
    }

    // Program → ConstDecl* CollDecl* VarDecl* FunctionDef*
    private ASTNode parseProgram() {
        ASTNode program = new ASTNode("Program", lookahead.line);
        while (!check(TokenType.EOF)) {
            if (check(TokenType.FINAL))
                program.addChild(parseConstDecl());
            else if (check(TokenType.COLL))
                program.addChild(parseCollDecl());
            else if (check(TokenType.DEF))
                program.addChild(parseFunctionDef());
            else if (isType())
                program.addChild(parseVarDecl());
            else
                throw new ParseException("Unexpected token '" + lookahead + "' at top level", lookahead.line);
        }
        return program;
    }

    // final INT i = 3;
    private ASTNode parseConstDecl() {
        int line = lookahead.line;
        match(TokenType.FINAL);
        ASTNode node = new ASTNode("ConstDecl", line);
        node.addChild(parseType());
        Symbol name = match(TokenType.IDENTIFIER);
        node.addChild(new LeafNode("Identifier", name.value, name.line));
        match(TokenType.ASSIGN);
        node.addChild(parseExpr());
        match(TokenType.SEMICOLON);
        return node;
    }

    // coll Point { INT x; INT y; }
    private ASTNode parseCollDecl() {
        int line = lookahead.line;
        match(TokenType.COLL);
        ASTNode node = new ASTNode("CollDecl", line);
        Symbol name = match(TokenType.COLLECTION_NAME);
        node.addChild(new LeafNode("CollectionName", name.value, name.line));
        match(TokenType.LBRACE);
        if (!isType())
            throw new ParseException("Collection must have at least one field", line);
        while (isType())
            node.addChild(parseFieldDecl());
        match(TokenType.RBRACE);
        return node;
    }

    private ASTNode parseFieldDecl() {
        int line = lookahead.line;
        ASTNode node = new ASTNode("FieldDecl", line);
        node.addChild(parseType());
        Symbol name = match(TokenType.IDENTIFIER);
        node.addChild(new LeafNode("Identifier", name.value, name.line));
        match(TokenType.SEMICOLON);
        return node;
    }

    // INT x = 3;  ou  INT x;
    private ASTNode parseVarDecl() {
        int line = lookahead.line;
        ASTNode node = new ASTNode("VarDecl", line);
        node.addChild(parseType());
        Symbol name = match(TokenType.IDENTIFIER);
        node.addChild(new LeafNode("Identifier", name.value, name.line));
        if (check(TokenType.ASSIGN)) {
            match(TokenType.ASSIGN);
            node.addChild(parseExpr());
        }
        match(TokenType.SEMICOLON);
        return node;
    }

    // def INT square(INT v) { ... }
    private ASTNode parseFunctionDef() {
        int line = lookahead.line;
        match(TokenType.DEF);
        ASTNode node = new ASTNode("FunctionDef", line);
        if (isType()) {
            ASTNode retType = parseType();
            String typeVal = (retType instanceof LeafNode) ? ((LeafNode) retType).getValue() : retType.getLabel();
            node.addChild(new LeafNode("ReturnType", typeVal, retType.getLine()));
        } else {
            node.addChild(new LeafNode("ReturnType", "void", line));
        }
        Symbol name = match(TokenType.IDENTIFIER);
        node.addChild(new LeafNode("Identifier", name.value, name.line));
        match(TokenType.LPAREN);
        node.addChild(parseParamList());
        match(TokenType.RPAREN);
        node.addChild(parseBlock());
        return node;
    }

    private ASTNode parseParamList() {
        ASTNode node = new ASTNode("ParamList", lookahead.line);
        if (check(TokenType.RPAREN)) return node;
        node.addChild(parseParam());
        while (check(TokenType.COMMA)) {
            match(TokenType.COMMA);
            node.addChild(parseParam());
        }
        return node;
    }

    private ASTNode parseParam() {
        int line = lookahead.line;
        ASTNode node = new ASTNode("Param", line);
        node.addChild(parseType());
        Symbol name = match(TokenType.IDENTIFIER);
        node.addChild(new LeafNode("Identifier", name.value, name.line));
        return node;
    }

    private ASTNode parseBlock() {
        int line = lookahead.line;
        match(TokenType.LBRACE);
        ASTNode node = new ASTNode("Block", line);
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF))
            node.addChild(parseStatement());
        match(TokenType.RBRACE);
        return node;
    }

    private ASTNode parseStatement() {
        if (isType())                       return parseVarDecl();
        if (check(TokenType.IF))            return parseIfStmt();
        if (check(TokenType.WHILE))         return parseWhileStmt();
        if (check(TokenType.FOR))           return parseForStmt();
        if (check(TokenType.RETURN))        return parseReturnStmt();
        if (check(TokenType.IDENTIFIER))    return parseIdentifierStatement();
        throw new ParseException("Unexpected token '" + lookahead + "' in statement", lookahead.line);
    }

    private ASTNode parseIdentifierStatement() {
        int line = lookahead.line;
        Symbol name = match(TokenType.IDENTIFIER);
        if (check(TokenType.ASSIGN)) {
            match(TokenType.ASSIGN);
            ASTNode node = new ASTNode("AssignStmt", line);
            node.addChild(new LeafNode("Identifier", name.value, name.line));
            node.addChild(parseExpr());
            match(TokenType.SEMICOLON);
            return node;
        } else if (check(TokenType.LBRACKET) || check(TokenType.DOT)) {
            ASTNode left = parseLvalueSuffix(new LeafNode("Identifier", name.value, name.line));
            match(TokenType.ASSIGN);
            ASTNode node = new ASTNode("AssignStmt", line);
            node.addChild(left);
            node.addChild(parseExpr());
            match(TokenType.SEMICOLON);
            return node;
        } else if (check(TokenType.LPAREN)) {
            ASTNode call = parseFunctionCallFrom(name);
            match(TokenType.SEMICOLON);
            ASTNode node = new ASTNode("ExprStmt", line);
            node.addChild(call);
            return node;
        }
        throw new ParseException("Unexpected token after identifier '" + name.value + "': " + lookahead, line);
    }

    private ASTNode parseLvalueSuffix(ASTNode left) {
        while (check(TokenType.DOT) || check(TokenType.LBRACKET)) {
            int line = lookahead.line;
            if (check(TokenType.DOT)) {
                match(TokenType.DOT);
                Symbol field = match(TokenType.IDENTIFIER);
                ASTNode node = new ASTNode("FieldAccess", line);
                node.addChild(left);
                node.addChild(new LeafNode("Identifier", field.value, field.line));
                left = node;
            } else {
                match(TokenType.LBRACKET);
                ASTNode idx = parseExpr();
                match(TokenType.RBRACKET);
                ASTNode node = new ASTNode("ArrayAccess", line);
                node.addChild(left);
                node.addChild(idx);
                left = node;
            }
        }
        return left;
    }

    private ASTNode parseIfStmt() {
        int line = lookahead.line;
        match(TokenType.IF);
        ASTNode node = new ASTNode("IfStmt", line);
        match(TokenType.LPAREN);
        node.addChild(parseExpr());
        match(TokenType.RPAREN);
        node.addChild(parseBlock());
        if (check(TokenType.ELSE)) {
            match(TokenType.ELSE);
            node.addChild(parseBlock());
        }
        return node;
    }

    private ASTNode parseWhileStmt() {
        int line = lookahead.line;
        match(TokenType.WHILE);
        ASTNode node = new ASTNode("WhileStmt", line);
        match(TokenType.LPAREN);
        node.addChild(parseExpr());
        match(TokenType.RPAREN);
        node.addChild(parseBlock());
        return node;
    }

    // for (INT idx; 1 -> 10; idx + 1) { ... }
    // ou for (idx; 1 -> 10; idx + 1) { ... }  (variable déjà déclarée)
    private ASTNode parseForStmt() {
        int line = lookahead.line;
        match(TokenType.FOR);
        ASTNode node = new ASTNode("ForStmt", line);
        match(TokenType.LPAREN);
        ASTNode varNode = new ASTNode("ForVar", line);
        if (isType()) {
            // for (INT i; ...)
            varNode.addChild(parseType());
            Symbol varName = match(TokenType.IDENTIFIER);
            varNode.addChild(new LeafNode("Identifier", varName.value, varName.line));
        } else {
            // for (i; ...)  — variable déjà déclarée
            Symbol varName = match(TokenType.IDENTIFIER);
            varNode.addChild(new LeafNode("Identifier", varName.value, varName.line));
        }
        node.addChild(varNode);
        match(TokenType.SEMICOLON);
        node.addChild(parseExpr());
        match(TokenType.ARROW);
        node.addChild(parseExpr());
        match(TokenType.SEMICOLON);
        node.addChild(parseExpr());
        match(TokenType.RPAREN);
        node.addChild(parseBlock());
        return node;
    }

    private ASTNode parseReturnStmt() {
        int line = lookahead.line;
        match(TokenType.RETURN);
        ASTNode node = new ASTNode("ReturnStmt", line);
        if (!check(TokenType.SEMICOLON))
            node.addChild(parseExpr());
        match(TokenType.SEMICOLON);
        return node;
    }

    // Hiérarchie de précédence : Or → And → Comparison → Addition → Multiply → Unary → Primary
    private ASTNode parseExpr() { return parseLogicalOr(); }

    private ASTNode parseLogicalOr() {
        ASTNode left = parseLogicalAnd();
        while (check(TokenType.OR)) {
            int line = lookahead.line;
            match(TokenType.OR);
            ASTNode node = new ASTNode("BinaryExpr: ||", line);
            node.addChild(left); node.addChild(parseLogicalAnd());
            left = node;
        }
        return left;
    }

    private ASTNode parseLogicalAnd() {
        ASTNode left = parseComparison();
        while (check(TokenType.AND)) {
            int line = lookahead.line;
            match(TokenType.AND);
            ASTNode node = new ASTNode("BinaryExpr: &&", line);
            node.addChild(left); node.addChild(parseComparison());
            left = node;
        }
        return left;
    }

    private ASTNode parseComparison() {
        ASTNode left = parseAddition();
        if (check(TokenType.EQUAL) || check(TokenType.NOT_EQUAL) ||
            check(TokenType.LT)    || check(TokenType.GT)        ||
            check(TokenType.LE)    || check(TokenType.GE)) {
            int line = lookahead.line;
            String op = lookahead.type.name();
            advance();
            ASTNode node = new ASTNode("BinaryExpr: " + op, line);
            node.addChild(left); node.addChild(parseAddition());
            return node;
        }
        return left;
    }

    private ASTNode parseAddition() {
        ASTNode left = parseMultiply();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            int line = lookahead.line;
            String op = lookahead.type == TokenType.PLUS ? "+" : "-";
            advance();
            ASTNode node = new ASTNode("BinaryExpr: " + op, line);
            node.addChild(left); node.addChild(parseMultiply());
            left = node;
        }
        return left;
    }

    private ASTNode parseMultiply() {
        ASTNode left = parseUnary();
        while (check(TokenType.TIMES) || check(TokenType.DIVIDE) || check(TokenType.MOD)) {
            int line = lookahead.line;
            String op = lookahead.type == TokenType.TIMES ? "*" : lookahead.type == TokenType.DIVIDE ? "/" : "%";
            advance();
            ASTNode node = new ASTNode("BinaryExpr: " + op, line);
            node.addChild(left); node.addChild(parseUnary());
            left = node;
        }
        return left;
    }

    private ASTNode parseUnary() {
        if (check(TokenType.MINUS)) {
            int line = lookahead.line;
            match(TokenType.MINUS);
            ASTNode node = new ASTNode("UnaryExpr: -", line);
            node.addChild(parsePrimary());
            return node;
        }
        return parsePrimary();
    }

    private ASTNode parsePrimary() {
        int line = lookahead.line;
        ASTNode result;

        if (check(TokenType.INTEGER_LIT)) {
            Symbol s = match(TokenType.INTEGER_LIT);
            result = new LeafNode("Integer", s.value, s.line);
        } else if (check(TokenType.FLOAT_LIT)) {
            Symbol s = match(TokenType.FLOAT_LIT);
            result = new LeafNode("FloatLiteral", s.value, s.line);
        } else if (check(TokenType.STRING_LIT)) {
            Symbol s = match(TokenType.STRING_LIT);
            result = new LeafNode("String", s.value, s.line);
        } else if (check(TokenType.TRUE)) {
            match(TokenType.TRUE);
            result = new LeafNode("BoolLiteral", "true", line);
        } else if (check(TokenType.FALSE)) {
            match(TokenType.FALSE);
            result = new LeafNode("BoolLiteral", "false", line);
        } else if (check(TokenType.IDENTIFIER)) {
            Symbol name = match(TokenType.IDENTIFIER);
            result = check(TokenType.LPAREN)
                ? parseFunctionCallFrom(name)
                : new LeafNode("Identifier", name.value, name.line);
        } else if (check(TokenType.COLLECTION_NAME)) {
            Symbol name = match(TokenType.COLLECTION_NAME);
            match(TokenType.LPAREN);
            ASTNode node = new ASTNode("CollConstructor", line);
            node.addChild(new LeafNode("CollectionName", name.value, name.line));
            node.addChild(parseArgList());
            match(TokenType.RPAREN);
            result = node;
        } else if (check(TokenType.LPAREN)) {
            match(TokenType.LPAREN);
            result = parseExpr();
            match(TokenType.RPAREN);
        } else if (isType()) {
            result = parseArrayCreation(line);
        } else {
            throw new ParseException("Unexpected token '" + lookahead + "' in expression", line);
        }

        return parsePrimarySuffix(result);
    }

    private ASTNode parsePrimarySuffix(ASTNode left) {
        while (check(TokenType.DOT) || check(TokenType.LBRACKET)) {
            int line = lookahead.line;
            if (check(TokenType.DOT)) {
                match(TokenType.DOT);
                Symbol field = match(TokenType.IDENTIFIER);
                ASTNode node = new ASTNode("FieldAccess", line);
                node.addChild(left);
                node.addChild(new LeafNode("Identifier", field.value, field.line));
                left = node;
            } else {
                match(TokenType.LBRACKET);
                ASTNode idx = parseExpr();
                match(TokenType.RBRACKET);
                ASTNode node = new ASTNode("ArrayAccess", line);
                node.addChild(left);
                node.addChild(idx);
                left = node;
            }
        }
        return left;
    }

    private ASTNode parseFunctionCallFrom(Symbol name) {
        int line = name.line;
        match(TokenType.LPAREN);
        ASTNode node = new ASTNode("FunctionCall", line);
        node.addChild(new LeafNode("Identifier", name.value, name.line));
        node.addChild(parseArgList());
        match(TokenType.RPAREN);
        return node;
    }

    private ASTNode parseArgList() {
        ASTNode node = new ASTNode("ArgList", lookahead.line);
        if (check(TokenType.RPAREN)) return node;
        node.addChild(parseExpr());
        while (check(TokenType.COMMA)) {
            match(TokenType.COMMA);
            node.addChild(parseExpr());
        }
        return node;
    }

    private ASTNode parseArrayCreation(int line) {
        ASTNode node = new ASTNode("ArrayCreation", line);
        node.addChild(parseType());
        match(TokenType.ARRAY);
        match(TokenType.LBRACKET);
        node.addChild(parseExpr());
        match(TokenType.RBRACKET);
        return node;
    }

    // Type → (INT | FLOAT | BOOL | STRING | COLLECTION_NAME) ('[]')?
    private ASTNode parseType() {
        int line = lookahead.line;
        String typeName;
        if (check(TokenType.INT))             { match(TokenType.INT);    typeName = "INT"; }
        else if (check(TokenType.FLOAT))      { match(TokenType.FLOAT);  typeName = "FLOAT"; }
        else if (check(TokenType.BOOL))       { match(TokenType.BOOL);   typeName = "BOOL"; }
        else if (check(TokenType.STRING))     { match(TokenType.STRING); typeName = "STRING"; }
        else if (check(TokenType.COLLECTION_NAME)) {
            Symbol s = match(TokenType.COLLECTION_NAME);
            typeName = s.value;
        } else {
            throw new ParseException("Expected a type but got '" + lookahead + "'", line);
        }
        // gère INT[], FLOAT[], Point[], etc.
        if (check(TokenType.LBRACKET)) {
            match(TokenType.LBRACKET);
            match(TokenType.RBRACKET);
            return new LeafNode("Type", typeName + "[]", line);
        }
        return new LeafNode("Type", typeName, line);
    }
}
