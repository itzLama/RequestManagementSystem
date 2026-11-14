"use client";

import * as echarts from "echarts/core";
import { BarChart, PieChart, type BarSeriesOption, type PieSeriesOption } from "echarts/charts";
import { GridComponent, LegendComponent, TooltipComponent, type GridComponentOption, type LegendComponentOption, type TooltipComponentOption } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";
import { useEffect, useRef } from "react";

// Only the pieces the dashboard uses are registered (keeps the bundle small).
echarts.use([BarChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer]);

export type ChartOption = echarts.ComposeOption<BarSeriesOption | PieSeriesOption | GridComponentOption | LegendComponentOption | TooltipComponentOption>;

/** Thin Apache ECharts wrapper: init once, replace the option on change, follow container size. */
export function EChart({ option, height, ariaLabel, summary }: { option: ChartOption; height: number; ariaLabel: string; summary: { label: string; value: number }[] }) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const chartRef = useRef<echarts.ECharts | null>(null);

  useEffect(() => {
    const element = containerRef.current;
    if (!element) return;
    const chart = echarts.init(element, undefined, { renderer: "canvas" });
    chartRef.current = chart;
    const observer = new ResizeObserver(() => chart.resize());
    observer.observe(element);
    return () => { observer.disconnect(); chart.dispose(); chartRef.current = null; };
  }, []);

  useEffect(() => { chartRef.current?.setOption(option, { notMerge: true }); }, [option]);

  return <figure className="m-0">
    <div ref={containerRef} role="img" aria-label={ariaLabel} style={{ height }} className="w-full" data-i18n-skip />
    {/* Screen-reader equivalent of the canvas chart. */}
    <figcaption className="sr-only" data-i18n-skip><ul>{summary.map((item) => <li key={item.label}>{item.label}: {item.value}</li>)}</ul></figcaption>
  </figure>;
}
