import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { BarChart, PieChart, TreeChart } from "echarts/charts";
import { GridComponent, LegendComponent, TooltipComponent } from "echarts/components";

// Registered once; every EChartCanvas instance renders through these pieces only.
use([CanvasRenderer, BarChart, PieChart, TreeChart, GridComponent, LegendComponent, TooltipComponent]);