package com.vidoc.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlanTest {

    @Test
    void formatsAmountWithTwoDecimals() {
        assertThat(Plan.MONTHLY_9.getTotalAmount()).isEqualTo("9.90");
        assertThat(Plan.MONTHLY.getTotalAmount()).isEqualTo("19.90");
        assertThat(Plan.YEARLY.getTotalAmount()).isEqualTo("189.00");
    }

    @Test
    void yearlyPeriodsAddUpToExactlyOneYear() {
        long year = 365L * 86400;

        assertThat(Plan.YEARLY.getPeriodSeconds() * Plan.YEARLY.getPeriods()).isEqualTo(year);
        assertThat(Plan.MONTHLY.getPeriodSeconds()).isEqualTo(30L * 86400);
    }

    @Test
    void looksUpByCodeAndRejectsUnknown() {
        assertThat(Plan.of("monthly_9")).isEqualTo(Plan.MONTHLY_9);
        assertThat(Plan.of("monthly")).isEqualTo(Plan.MONTHLY);
        assertThat(Plan.of("yearly")).isEqualTo(Plan.YEARLY);
        assertThat(Plan.of("topup_10")).isEqualTo(Plan.TOPUP_10);
        assertThat(Plan.of("topup_50")).isEqualTo(Plan.TOPUP_50);
        assertThat(Plan.of("topup_100")).isEqualTo(Plan.TOPUP_100);
        assertThat(Plan.of("free")).isNull();
        assertThat(Plan.of("MONTHLY")).isNull();
    }

    @Test
    void distinguishesSubscriptionsFromTopUps() {
        assertThat(Plan.MONTHLY.getKind()).isEqualTo(PlanKind.SUBSCRIPTION);
        assertThat(Plan.YEARLY.getKind()).isEqualTo(PlanKind.SUBSCRIPTION);
        assertThat(Plan.TOPUP_10.getKind()).isEqualTo(PlanKind.TOPUP);
        assertThat(Plan.TOPUP_50.getKind()).isEqualTo(PlanKind.TOPUP);
        assertThat(Plan.TOPUP_100.getKind()).isEqualTo(PlanKind.TOPUP);
    }

    @Test
    void topUpsGrantEverythingAtOnceAndNeverDrip() {
        for (Plan plan : Plan.values()) {
            if (plan.getKind() != PlanKind.TOPUP) {
                continue;
            }
            assertThat(plan.getPeriods()).isEqualTo(1);
            assertThat(plan.getPeriodSeconds()).isZero();
            assertThat(plan.getPeriodPoints()).isPositive();
        }
        assertThat(Plan.TOPUP_10.getTotalAmount()).isEqualTo("10.00");
        assertThat(Plan.TOPUP_50.getTotalAmount()).isEqualTo("50.00");
        assertThat(Plan.TOPUP_100.getTotalAmount()).isEqualTo("100.00");
    }

    /** 定价铁律：充值是「单独买」，单位积分必须比订阅这个打包价更贵，否则没人订阅。 */
    @Test
    void topUpsAlwaysCostMorePerPointThanSubscriptions() {
        for (Plan topUp : Plan.values()) {
            if (topUp.getKind() != PlanKind.TOPUP) {
                continue;
            }
            double topUpRate = topUp.getPeriodPoints() * 100.0 / topUp.getAmountFen();
            for (Plan subscription : Plan.values()) {
                if (subscription.getKind() != PlanKind.SUBSCRIPTION) {
                    continue;
                }
                double subscriptionRate = subscription.getPeriodPoints() * (double) subscription.getPeriods() * 100
                        / subscription.getAmountFen();
                assertThat(topUpRate)
                        .as("%s 的单位积分不能比 %s 划算", topUp.getCode(), subscription.getCode())
                        .isLessThan(subscriptionRate);
            }
        }
    }
}
