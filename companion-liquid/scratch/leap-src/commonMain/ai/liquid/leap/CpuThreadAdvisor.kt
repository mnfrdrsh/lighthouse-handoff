@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.liquid.leap

expect object CpuThreadAdvisor {
  fun getRecommendedThreadCount(): Int
}
