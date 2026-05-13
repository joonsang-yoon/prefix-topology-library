package prefix.topology.library

import java.math.RoundingMode

private[library] object ParetoFrontierDiagram {
  private val SvgWidthPx = 720
  private val SvgHeightPx = 480
  private val MarginTopPx = 56
  private val MarginRightPx = 40
  private val MarginBottomPx = 68
  private val MarginLeftPx = 96
  private val PlotWidthPx = SvgWidthPx - MarginLeftPx - MarginRightPx
  private val PlotHeightPx = SvgHeightPx - MarginTopPx - MarginBottomPx
  private val PlotRightPx = MarginLeftPx + PlotWidthPx
  private val PlotBottomPx = MarginTopPx + PlotHeightPx
  private val TickCount = 5

  def render(
    width:    Int,
    points:   Seq[ParetoFrontier.Point],
    frontier: Seq[ParetoFrontier.Point]
  ): String = {
    val xDomain = axisDomain(points.map(_.areaUm2))
    val yDomain = axisDomain(points.map(_.clockPeriodNs))
    val frontierLabels = ParetoFrontier.entries(frontier).map { case (area, _, point) => point -> area }.toMap

    def x(value: Double): Int = MarginLeftPx + scale(value, xDomain, PlotWidthPx)
    def y(value: Double): Int = PlotBottomPx - scale(value, yDomain, PlotHeightPx)

    def xTick(value: Double): String = {
      val xCoord = x(value)
      val label = formatNumber(value)
      s"""  <line x1="${xCoord}" y1="${PlotBottomPx}" x2="${xCoord}" y2="${PlotBottomPx + 6}" stroke="#111111" stroke-width="1"/>
         |  <text x="${xCoord}" y="${PlotBottomPx + 24}" text-anchor="middle" font-size="12" fill="#333333">${label}</text>
         |""".stripMargin
    }

    def yTick(value: Double): String = {
      val yCoord = y(value)
      val label = formatNumber(value)
      s"""  <line x1="${MarginLeftPx - 6}" y1="${yCoord}" x2="${MarginLeftPx}" y2="${yCoord}" stroke="#111111" stroke-width="1"/>
         |  <text x="${MarginLeftPx - 10}" y="${yCoord + 4}" text-anchor="end" font-size="12" fill="#333333">${label}</text>
         |""".stripMargin
    }

    def renderPoint(point: ParetoFrontier.Point): String = {
      val xCoord = x(point.areaUm2)
      val yCoord = y(point.clockPeriodNs)
      val frontierLabel = frontierLabels.get(point)
      val (fill, stroke, radius) =
        if (frontierLabel.isDefined) ("#b91c1c", "#7f1d1d", 7)
        else ("#94a3b8", "#475569", 5)
      val labelSvg = frontierLabel.fold("") { label =>
        s"""  <text x="${xCoord + 10}" y="${yCoord - 10}" font-size="12" fill="#111111">${label}</text>
           |""".stripMargin
      }

      s"""  <circle cx="${xCoord}" cy="${yCoord}" r="${radius}" fill="${fill}" stroke="${stroke}" stroke-width="1.5"/>
         |${labelSvg}""".stripMargin
    }

    val frontierPath =
      frontier
        .map(point => s"${x(point.areaUm2)},${y(point.clockPeriodNs)}")
        .mkString("M ", " L ", "")
    val tickSvg = (tickValues(xDomain).map(xTick) ++ tickValues(yDomain).map(yTick)).mkString
    val pointSvg = points.sortBy(_.areaSortKey).iterator.map(renderPoint).mkString

    s"""<svg xmlns="http://www.w3.org/2000/svg" width="${SvgWidthPx}" height="${SvgHeightPx}" viewBox="0 0 ${SvgWidthPx} ${SvgHeightPx}" font-family="sans-serif">
       |  <rect width="${SvgWidthPx}" height="${SvgHeightPx}" fill="white"/>
       |  <line x1="${MarginLeftPx}" y1="${PlotBottomPx}" x2="${PlotRightPx}" y2="${PlotBottomPx}" stroke="#111111" stroke-width="1.5"/>
       |  <line x1="${MarginLeftPx}" y1="${MarginTopPx}" x2="${MarginLeftPx}" y2="${PlotBottomPx}" stroke="#111111" stroke-width="1.5"/>
       |${tickSvg}  <text x="${MarginLeftPx}" y="28" font-size="22" font-weight="bold" fill="#111111">Pareto frontier for width ${width}</text>
       |  <text x="${MarginLeftPx + PlotWidthPx / 2}" y="${SvgHeightPx - 20}" text-anchor="middle" font-size="14" fill="#111111">${ParetoFrontier.areaUm2} (minimize)</text>
       |  <text x="24" y="${MarginTopPx + PlotHeightPx / 2}" text-anchor="middle" font-size="14" fill="#111111" transform="rotate(-90 24 ${MarginTopPx + PlotHeightPx / 2})">${ParetoFrontier.clockPeriodNs} (minimize)</text>
       |  <path d="${frontierPath}" fill="none" stroke="#b91c1c" stroke-width="3"/>
       |${pointSvg}</svg>
       |""".stripMargin
  }

  private def tickValues(domain: (Double, Double)): Vector[Double] =
    Vector.tabulate(TickCount + 1) { index =>
      domain._1 + (domain._2 - domain._1) * index / TickCount
    }

  private def axisDomain(values: Seq[Double]): (Double, Double) = {
    val min = values.min
    val max = values.max
    val padding =
      if (min == max) math.max(math.abs(min) * 0.05, 1e-6)
      else (max - min) * 0.08
    (min - padding, max + padding)
  }

  private def scale(value: Double, domain: (Double, Double), pixels: Int): Int = {
    val (min, max) = domain
    math.round((value - min) / (max - min) * pixels).toInt
  }

  private def formatNumber(value: Double): String =
    BigDecimal(value).bigDecimal.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros.toPlainString
}
