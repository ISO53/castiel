<template>
	<VChart class="h-full w-full min-h-0" :option="mergedOption" autoresize />
</template>

<script setup>
import VChart from "vue-echarts";
import "@/lib/echarts-setup";

/**
 * Thin vue-echarts wrapper. Charts stay transparent so they sit on the app background;
 * pass any ECharts option through {@prop option}.
 */
const props = defineProps({
	option: { type: Object, required: true },
});

const mergedOption = {
	backgroundColor: "transparent",
	textStyle: { color: "#a1a1aa", fontFamily: "inherit" },
	...props.option,
};
</script>