package com.khs.weeklyreport.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class KanbanCardTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 9);

    @Test
    void 완료일이_없으면_남은_날도_임박도_없다() {
        KanbanCard card = card(KanbanStatus.ING, null);

        assertThat(card.daysUntilDue(TODAY)).isNull();
        assertThat(card.isDueSoon(TODAY, 3)).isFalse();
    }

    @Test
    void 남은_날을_일_단위로_센다() {
        assertThat(card(KanbanStatus.ING, TODAY).daysUntilDue(TODAY)).isZero();
        assertThat(card(KanbanStatus.ING, TODAY.plusDays(3)).daysUntilDue(TODAY)).isEqualTo(3);
        assertThat(card(KanbanStatus.ING, TODAY.minusDays(2)).daysUntilDue(TODAY)).isEqualTo(-2);
        // 달을 넘겨도 일수로 센다
        assertThat(card(KanbanStatus.ING, LocalDate.of(2026, 10, 9)).daysUntilDue(TODAY)).isEqualTo(30);
    }

    @Test
    void 사흘_이내면_임박이고_나흘부터는_아니다() {
        assertThat(card(KanbanStatus.TODO, TODAY.plusDays(3)).isDueSoon(TODAY, 3)).isTrue();
        assertThat(card(KanbanStatus.TODO, TODAY.plusDays(4)).isDueSoon(TODAY, 3)).isFalse();
    }

    @Test
    void 이미_지난_카드도_임박으로_친다() {
        // 놓친 일이 목록에서 조용히 빠지면 알림의 의미가 없다
        assertThat(card(KanbanStatus.ING, TODAY.minusDays(10)).isDueSoon(TODAY, 3)).isTrue();
    }

    @Test
    void 완료된_카드는_임박에서_뺀다() {
        assertThat(card(KanbanStatus.DONE, TODAY).isDueSoon(TODAY, 3)).isFalse();
        assertThat(card(KanbanStatus.DONE, TODAY.minusDays(10)).isDueSoon(TODAY, 3)).isFalse();
    }

    private KanbanCard card(KanbanStatus status, LocalDate dueDate) {
        KanbanCard card = new KanbanCard();
        card.setStatus(status);
        card.setTitle("카드");
        card.setDueDate(dueDate);
        return card;
    }
}
