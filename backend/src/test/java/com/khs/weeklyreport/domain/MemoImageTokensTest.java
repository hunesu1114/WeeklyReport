package com.khs.weeklyreport.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoImageTokensTest {

    @Test
    void 본문에_나온_순서대로_겹치지_않게_아이디를_꺼낸다() {
        String content = """
                ![둘째](img:Qm81LxV0pa)
                글
                ![첫째](img:k3F9aQ2xZ7) 줄 가운데에도 ![둘째 다시](img:Qm81LxV0pa)
                """;

        assertThat(MemoImageTokens.ids(content)).containsExactly("Qm81LxV0pa", "k3F9aQ2xZ7");
    }

    @Test
    void 모양이_어긋난_것은_토큰이_아니다() {
        // 아이디가 짧거나 길거나, 설명에 줄 바꿈이나 ] 가 들어가면 그냥 글이다
        String content = """
                ![짧다](img:abc)
                ![길다](img:k3F9aQ2xZ7X)
                ![줄
                바꿈](img:k3F9aQ2xZ7)
                ![a]b](img:k3F9aQ2xZ7)
                [그림 아님](img:k3F9aQ2xZ7)
                """;

        assertThat(MemoImageTokens.ids(content)).isEmpty();
    }

    @Test
    void 토큰을_사람이_읽는_말로_바꾼다() {
        assertThat(MemoImageTokens.toText("앞 ![로그인 오류](img:k3F9aQ2xZ7) 뒤\n![ ](img:Qm81LxV0pa)"))
                .isEqualTo("앞 [그림: 로그인 오류] 뒤\n[그림]");
    }

    @Test
    void 설명의_달러와_역슬래시도_그대로_옮긴다() {
        // 바꾸기 문자열에서 $ 와 \ 는 특별한 뜻이 있다. 설명에 들어 있어도 깨지면 안 된다
        assertThat(MemoImageTokens.toText("![C:\\temp $1](img:k3F9aQ2xZ7)")).isEqualTo("[그림: C:\\temp $1]");
    }

    @Test
    void 토큰이_없으면_그대로_돌려준다() {
        assertThat(MemoImageTokens.toText("그냥 글")).isEqualTo("그냥 글");
        assertThat(MemoImageTokens.toText(null)).isNull();
        assertThat(MemoImageTokens.count(null)).isZero();
    }
}
