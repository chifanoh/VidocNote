<script setup lang="ts">
import { subscribePlans } from '~/utils/plans'

const plans = computed(() => subscribePlans.map(plan => ({
  ...plan,
  button: {
    ...plan.button,
    to: '/dashboard/plans'
  }
})))
</script>

<template>
  <section id="pricing" class="Home_homeSection pricing">
    <div class="Home_homeSectionHeader">
      <h2>订阅</h2>
    </div>
    <UPricingPlans :plans="plans"/>
  </section>
</template>
