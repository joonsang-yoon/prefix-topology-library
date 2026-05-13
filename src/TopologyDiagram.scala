package prefix.topology.library

import TopologyLayout.PlacedCell

private[library] object TopologyDiagram {
  private val LabelFontSizePx = 12
  private val LabelHorizontalPaddingPx = 4
  private val LabelVerticalPaddingPx = 2
  private val EstimatedCharacterWidthPx = 8
  private val StrokeWidthPx = 2

  private[library] def render(topology: PrefixTopology): String = {
    val cells = TopologyLayout.place(topology)
    val cellRadiusPx = cellRadius(topology.width)
    val gridSpacingPx = cellRadiusPx * 4
    val svgWidthPx = topology.width * gridSpacingPx
    val svgHeightPx = (cells.iterator.map(_.row).max + 1) * gridSpacingPx
    val centers = cells.iterator.map { cell =>
      val x = cell.id.high * gridSpacingPx + gridSpacingPx / 2
      val y = cell.row * gridSpacingPx + gridSpacingPx / 2
      cell.id -> (x, y)
    }.toMap

    def renderWire(source: CellRef, destination: CellRef): String = {
      val (x1, y1) = centers(source)
      val (x2, y2) = centers(destination)
      val controlX = (x1 + x2) / 2 - (y2 - y1) / 4
      val controlY = (y1 + y2) / 2 + (x2 - x1) / 4

      s"""  <path d="M ${x1} ${y1} Q ${controlX} ${controlY} ${x2} ${y2}" fill="none" stroke="black" stroke-width="${StrokeWidthPx}"/>
         |""".stripMargin
    }

    def renderCell(cell: PlacedCell): String = {
      val (x, y) = centers(cell.id)
      val (fill, textFill) =
        if (cell.id.isLeaf) ("white", "black")
        else if (cell.isBlack) ("black", "white")
        else ("silver", "black")
      val text = label(cell.id)

      s"""  <circle cx="${x}" cy="${y}" r="${cellRadiusPx}" fill="${fill}" stroke="black" stroke-width="${StrokeWidthPx}"/>
         |  <text x="${x}" y="${y}" text-anchor="middle" dominant-baseline="middle" font-size="${LabelFontSizePx}" fill="${textFill}">${text}</text>
         |""".stripMargin
    }

    val wireSvg = topology.cells.iterator.flatMap { cell =>
      Iterator(cell.left, cell.right).map(source => renderWire(source, cell.target))
    }.mkString
    val cellSvg = cells.iterator.map(renderCell).mkString

    s"""<svg xmlns="http://www.w3.org/2000/svg" width="${svgWidthPx}" height="${svgHeightPx}" viewBox="0 0 ${svgWidthPx} ${svgHeightPx}" font-family="sans-serif">
       |${wireSvg}${cellSvg}</svg>
       |""".stripMargin
  }

  private def label(cell: CellRef): String =
    s"[${cell.low}, ${cell.high}]"

  private def cellRadius(width: Int): Int = {
    val labelWidth = label(PrefixTree.Leaf(width - 1)).length * EstimatedCharacterWidthPx +
      LabelHorizontalPaddingPx * 2
    val labelHeight = LabelFontSizePx + LabelVerticalPaddingPx * 2
    math.ceil((math.hypot(labelWidth, labelHeight) + StrokeWidthPx) / 2).toInt
  }
}
