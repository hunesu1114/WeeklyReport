package com.khs.weeklyreport.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 본문 속 그림 토큰 {@code ![설명](img:아이디)}.
 *
 * <p>마크다운 그림 문법을 빌렸다. txt 로 내보내도 사람이 읽을 수 있고, 다른 편집기에
 * 붙여넣어도 무엇이 있던 자리인지 알 수 있다. 설명에는 ] 와 줄 바꿈이 들어갈 수 없다 —
 * 들어가면 토큰이 아니라 그냥 글로 본다.
 */
public final class MemoImageTokens {

    /** 아이디는 브라우저가 만드는 10자. 길이가 다르면 토큰으로 보지 않는다. */
    public static final Pattern ID = Pattern.compile("[A-Za-z0-9]{10}");

    public static final Pattern TOKEN = Pattern.compile("!\\[([^\\]\\n]*)\\]\\(img:([A-Za-z0-9]{10})\\)");

    /** 본문에 나온 토큰 하나. 설명은 앞뒤 빈칸을 걷어 낸 것이다. */
    public record Token(String alt, String id) {
    }

    private MemoImageTokens() {
    }

    /** 본문에 나온 토큰 전부. 같은 그림이 두 번 나오면 두 번 다. */
    public static List<Token> tokens(String content) {
        if (content == null || content.indexOf("(img:") < 0) return List.of();
        List<Token> out = new ArrayList<>();
        Matcher m = TOKEN.matcher(content);
        while (m.find()) out.add(new Token(m.group(1).strip(), m.group(2)));
        return out;
    }

    /** 본문에 나온 순서대로, 겹치지 않게. */
    public static List<String> ids(String content) {
        Set<String> ids = new LinkedHashSet<>();
        for (Token token : tokens(content)) ids.add(token.id());
        return new ArrayList<>(ids);
    }

    public static int count(String content) {
        return ids(content).size();
    }

    /** 토큰마다 바꿀 글을 정해 바꾼다. 토큰이 아닌 글은 그대로 둔다. */
    public static String replace(String content, BiFunction<String, String, String> replacement) {
        if (content == null || content.indexOf("(img:") < 0) return content;
        Matcher m = TOKEN.matcher(content);
        StringBuilder out = new StringBuilder(content.length());
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(replacement.apply(m.group(1).strip(), m.group(2))));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * 토큰을 사람이 읽는 말로 바꾼다. txt 내보내기 · 목록 미리보기 · 검색 토막이 쓴다.
     * 설명이 있으면 {@code [그림: 설명]}, 없으면 {@code [그림]}.
     */
    public static String toText(String content) {
        return replace(content, (alt, id) -> label(alt));
    }

    /** 그림을 내보낼 수 없을 때 남기는 말. */
    public static String label(String alt) {
        return alt == null || alt.isBlank() ? "[그림]" : "[그림: " + alt.strip() + "]";
    }
}
