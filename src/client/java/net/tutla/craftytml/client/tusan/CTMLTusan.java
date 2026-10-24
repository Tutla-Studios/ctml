package net.tutla.craftytml.client.tusan;

import net.tutla.craftytml.client.tusan.node.ElementNode;
import net.tutla.craftytml.client.tusan.register.ElementRegistry;
import net.tutla.craftytml.client.tusan.register.NodeRegistry;
import net.tutla.craftytml.client.tusan.register.PropertyRegistry;
import net.tutla.tums.tusan.lang.Tusan;
import net.tutla.tums.tusan.lexer.LexerRule;
import net.tutla.tums.tusan.lexer.util.WordedPattern;
import net.tutla.tums.tusan.lexer.util.prebuilt.PrebuiltTusanTokenType;

import java.util.ArrayList;
import java.util.List;

public class CTMLTusan extends Tusan {
    private static final List<LexerRule> LEXER_RULES = new ArrayList<>(List.of(
            //new LexerRule(CTMLTokenType.OPENING, "(<[a-zA-Z][a-zA-Z0-9-]*)\\b[^>]*(>)"),

            new LexerRule(PrebuiltTusanTokenType.STRING, "\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'"),
            new LexerRule(PrebuiltTusanTokenType.STRING, "[^<>\\s](?:[^<>]*[^<>\\s])?(?=[^<>]*<)"),
            new LexerRule(PrebuiltTusanTokenType.NUMBER, "\\d+(\\.\\d+)?"),
            new LexerRule(PrebuiltTusanTokenType.BOOL, WordedPattern.makeWordedPattern(List.of("true", "false")) ),
            new LexerRule(PrebuiltTusanTokenType.NOTHING, WordedPattern.makeOneWordPattern("nothing")),

            new LexerRule(PrebuiltTusanTokenType.OPERATOR, "[+\\-*/%]"),
            new LexerRule(PrebuiltTusanTokenType.LOGIC, "(?:\\b(?:and|or|not|contains|in)\\b|\\|\\||&&)"),
            //new LexerRule(PrebuiltTusanTokenType.COMPARISON, ">=|<=|==|!=|>|<|\\bis\\b"),
            new LexerRule(PrebuiltTusanTokenType.EQUAL, "="),

            new LexerRule(PrebuiltTusanTokenType.LEFT_CURLY, WordedPattern.makeSpecialCharacterPattern('{')),
            new LexerRule(PrebuiltTusanTokenType.RIGHT_CURLY, WordedPattern.makeSpecialCharacterPattern('}')),
            new LexerRule(PrebuiltTusanTokenType.LEFT_PAR, WordedPattern.makeSpecialCharacterPattern('(')),
            new LexerRule(PrebuiltTusanTokenType.RIGHT_PAR, WordedPattern.makeSpecialCharacterPattern(')')),
            new LexerRule(PrebuiltTusanTokenType.LEFT_SQUARE, WordedPattern.makeSpecialCharacterPattern('[')),
            new LexerRule(PrebuiltTusanTokenType.RIGHT_SQUARE, WordedPattern.makeSpecialCharacterPattern(']')),
            new LexerRule(PrebuiltTusanTokenType.SEMICOLON, ";"),
            new LexerRule(PrebuiltTusanTokenType.COLON, ":"),
            new LexerRule(PrebuiltTusanTokenType.COMMA, ",")
    ));

    private static boolean initialized = false;

    public static void init(){
        if (initialized) return;
        initialized = true;

        ElementRegistry.init();
        PropertyRegistry.init();
        NodeRegistry.init();

        ElementRegistry.getRegister().forEach((element -> {
            LEXER_RULES.add(new LexerRule(element.tokenType, "(<"+element.name+")\\b[^>]*(>)").setGroup(CTMLTokenGroup.ELEMENT));
            LEXER_RULES.add(new LexerRule(element.closingTokenType, "</"+element.name+"\\s*>"));
        }));

        PropertyRegistry.getRegister().forEach((property -> {
            LEXER_RULES.add(new LexerRule(property.tokenType, "\\b"+property.name+"\\b(?=(?:\"[^\"]*\"|'[^']*'|[^<>])*>)"));
        }));


    }

    public static CTMLTusan tusan(){
        init();
        CTMLTusan tsn = new CTMLTusan();

        LEXER_RULES.forEach(tsn::registerLexerRule);
        tsn.setStatementNode(ElementNode.class);
        return tsn;
    }
}
