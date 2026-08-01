import SwiftUI

/// Full-screen "See the read" viewer — Sprint 2 X-Ray + Fit Map overlay.
/// Renders editorial markup (arrows / lines / focus rings / swap flags) on top
/// of the outfit photo, with a toggle for the fit-tension heatmap. Coords are
/// normalized [0,1] so they scale to any photo size.
struct ScoreAnnotationView: View {
    let photoUrl: String
    let annotations: [MarkupAnnotation]
    let fitMap: FitMap?
    let hemComment: String
    var onClose: () -> Void

    @State private var showFitMap = false
    @State private var showAnnotations = true
    @State private var focusedAnnotation: Int? = nil
    @State private var revealPhase: Double = 0   // 0..1 stagger progress

    var body: some View {
        GeometryReader { geo in
            ZStack {
                Palette.paper.ignoresSafeArea()

                VStack(spacing: 0) {
                    // Header
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Eyebrow(text: "HEM'S READ")
                            Text("The markup.")
                                .font(Serif.display(22))
                                .foregroundStyle(Palette.ink)
                        }
                        Spacer()
                        Button(action: onClose) {
                            Image(systemName: "xmark")
                                .foregroundStyle(Palette.ink)
                                .frame(width: 36, height: 36)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 12)

                    // Photo + overlays
                    ZStack {
                        AsyncImage(url: URL(string: photoUrl)) { img in
                            img.resizable().scaledToFit()
                        } placeholder: {
                            RoundedRectangle(cornerRadius: 14).fill(Palette.card)
                        }
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                        .overlay(
                            GeometryReader { photoGeo in
                                ZStack {
                                    if showFitMap, let fm = fitMap {
                                        FitHeatmap(fitMap: fm)
                                            .frame(width: photoGeo.size.width, height: photoGeo.size.height)
                                            .clipShape(RoundedRectangle(cornerRadius: 14))
                                            .opacity(0.55)
                                            .allowsHitTesting(false)
                                    }
                                    if showAnnotations {
                                        AnnotationLayer(
                                            annotations: annotations,
                                            focused: focusedAnnotation,
                                            revealPhase: revealPhase,
                                            size: photoGeo.size
                                        )
                                    }
                                }
                            }
                        )
                        .padding(.horizontal, 20)
                    }

                    // Toggle strip
                    HStack(spacing: 8) {
                        toggleChip(
                            title: "Markup",
                            icon: "square.on.square",
                            active: showAnnotations
                        ) { showAnnotations.toggle() }
                        if fitMap != nil {
                            toggleChip(
                                title: "Fit map",
                                icon: "square.grid.3x3.fill",
                                active: showFitMap
                            ) { showFitMap.toggle() }
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)

                    // Bottom sheet — essay + chip list
                    ScrollView {
                        VStack(alignment: .leading, spacing: 14) {
                            if !hemComment.isEmpty {
                                Text(hemComment)
                                    .font(Serif.italic(16))
                                    .foregroundStyle(Palette.ink.opacity(0.85))
                            }
                            if !annotations.isEmpty {
                                Eyebrow(text: "NOTES")
                                    .padding(.top, 4)
                                VStack(alignment: .leading, spacing: 8) {
                                    ForEach(Array(annotations.enumerated()), id: \.offset) { idx, a in
                                        Button(action: {
                                            Haptic.chip()
                                            focusedAnnotation = focusedAnnotation == idx ? nil : idx
                                        }) {
                                            HStack(alignment: .top, spacing: 10) {
                                                markerIcon(for: a.type)
                                                    .frame(width: 22)
                                                Text(a.note)
                                                    .font(Serif.body(14))
                                                    .foregroundStyle(Palette.ink)
                                                    .multilineTextAlignment(.leading)
                                                Spacer()
                                            }
                                            .padding(10)
                                            .background(focusedAnnotation == idx ? Palette.card : Color.clear)
                                            .overlay(
                                                RoundedRectangle(cornerRadius: 10)
                                                    .stroke(focusedAnnotation == idx ? Palette.bronze : Palette.hairline, lineWidth: 1)
                                            )
                                            .clipShape(RoundedRectangle(cornerRadius: 10))
                                        }
                                        .buttonStyle(.plain)
                                    }
                                }
                            }
                            if let hotspots = fitMap?.hotspots, !hotspots.isEmpty {
                                Eyebrow(text: "FIT HOTSPOTS").padding(.top, 8)
                                VStack(alignment: .leading, spacing: 6) {
                                    ForEach(Array(hotspots.enumerated()), id: \.offset) { _, h in
                                        HStack(alignment: .top, spacing: 8) {
                                            Circle()
                                                .fill(h.severity > 0.5 ? Color(red: 0.72, green: 0.33, blue: 0.31) : Color(red: 0.23, green: 0.43, blue: 0.65))
                                                .frame(width: 6, height: 6)
                                                .padding(.top, 8)
                                            Text(h.label)
                                                .font(Serif.body(13))
                                                .foregroundStyle(Palette.ink)
                                        }
                                    }
                                }
                            }
                            Spacer(minLength: 20)
                        }
                        .padding(20)
                    }
                    .frame(maxHeight: geo.size.height * 0.35)
                }
            }
            .onAppear {
                // Staggered reveal — annotations fade in one by one over ~1s
                withAnimation(.easeOut(duration: 1.0)) { revealPhase = 1.0 }
            }
        }
    }

    private func toggleChip(title: String, icon: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: { Haptic.chip(); action() }) {
            HStack(spacing: 6) {
                Image(systemName: icon).font(.system(size: 11))
                Text(title).font(Serif.body(13, weight: .medium))
            }
            .foregroundStyle(active ? .white : Palette.ink)
            .padding(.horizontal, 12).padding(.vertical, 8)
            .background(active ? Palette.ink : Color.clear)
            .overlay(Capsule().stroke(active ? Palette.ink : Palette.hairline, lineWidth: 1))
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func markerIcon(for type: String) -> some View {
        switch type {
        case "arrow": Image(systemName: "arrow.up.right").font(.system(size: 12, weight: .bold)).foregroundStyle(Palette.bronze)
        case "line":  Image(systemName: "minus").font(.system(size: 14, weight: .bold)).foregroundStyle(Palette.bronze)
        case "focus": Image(systemName: "circle").font(.system(size: 12, weight: .bold)).foregroundStyle(Palette.bronze)
        case "swap":  Image(systemName: "diamond.fill").font(.system(size: 10, weight: .bold)).foregroundStyle(Palette.bronze)
        default:      Image(systemName: "circle.fill").font(.system(size: 8)).foregroundStyle(Palette.bronze)
        }
    }
}

// MARK: - Annotation layer (arrows/lines/focus/swap)

private struct AnnotationLayer: View {
    let annotations: [MarkupAnnotation]
    let focused: Int?
    let revealPhase: Double
    let size: CGSize

    var body: some View {
        Canvas { ctx, canvasSize in
            let stroke = Palette.bronze
            let strokeColor = GraphicsContext.Shading.color(stroke)

            for (idx, a) in annotations.enumerated() {
                let visible = Double(idx + 1) / Double(annotations.count) <= revealPhase + 0.05
                if !visible { continue }
                let opacity = focused == nil || focused == idx ? 1.0 : 0.35
                ctx.opacity = opacity

                switch a.type {
                case "arrow":
                    if let f = a.coords.from, let t = a.coords.to, f.count == 2, t.count == 2 {
                        let p1 = CGPoint(x: canvasSize.width * f[0], y: canvasSize.height * f[1])
                        let p2 = CGPoint(x: canvasSize.width * t[0], y: canvasSize.height * t[1])
                        var line = Path()
                        line.move(to: p1)
                        line.addLine(to: p2)
                        ctx.stroke(line, with: strokeColor, lineWidth: focused == idx ? 2.0 : 1.4)
                        // arrow head
                        let angle = atan2(p2.y - p1.y, p2.x - p1.x)
                        let head = 10.0
                        var arrow = Path()
                        arrow.move(to: p2)
                        arrow.addLine(to: CGPoint(x: p2.x - cos(angle - Double.pi/6) * head, y: p2.y - sin(angle - Double.pi/6) * head))
                        arrow.move(to: p2)
                        arrow.addLine(to: CGPoint(x: p2.x - cos(angle + Double.pi/6) * head, y: p2.y - sin(angle + Double.pi/6) * head))
                        ctx.stroke(arrow, with: strokeColor, lineWidth: focused == idx ? 2.0 : 1.4)
                    }
                case "line":
                    if let f = a.coords.from, let t = a.coords.to, f.count == 2, t.count == 2 {
                        let p1 = CGPoint(x: canvasSize.width * f[0], y: canvasSize.height * f[1])
                        let p2 = CGPoint(x: canvasSize.width * t[0], y: canvasSize.height * t[1])
                        var line = Path()
                        line.move(to: p1)
                        line.addLine(to: p2)
                        ctx.stroke(line, with: strokeColor,
                                   style: StrokeStyle(lineWidth: focused == idx ? 2 : 1.2, dash: [4, 4]))
                    }
                case "focus":
                    if let at = a.coords.at, at.count == 2 {
                        let c = CGPoint(x: canvasSize.width * at[0], y: canvasSize.height * at[1])
                        let r = canvasSize.width * (a.coords.radius ?? 0.08)
                        let rect = CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)
                        ctx.stroke(Path(ellipseIn: rect), with: strokeColor, lineWidth: focused == idx ? 2 : 1.4)
                    }
                case "swap":
                    if let at = a.coords.at, at.count == 2 {
                        let c = CGPoint(x: canvasSize.width * at[0], y: canvasSize.height * at[1])
                        var diamond = Path()
                        let s = focused == idx ? 9.0 : 7.0
                        diamond.move(to: CGPoint(x: c.x, y: c.y - s))
                        diamond.addLine(to: CGPoint(x: c.x + s, y: c.y))
                        diamond.addLine(to: CGPoint(x: c.x, y: c.y + s))
                        diamond.addLine(to: CGPoint(x: c.x - s, y: c.y))
                        diamond.closeSubpath()
                        ctx.fill(diamond, with: strokeColor)
                    }
                default: break
                }
            }
        }
        .allowsHitTesting(false)
    }
}

// MARK: - Fit heatmap

private struct FitHeatmap: View {
    let fitMap: FitMap

    var body: some View {
        Canvas { ctx, size in
            guard let grid = fitMap.grid, !grid.isEmpty else { return }
            let rows = grid.count
            let cols = grid.first?.count ?? 0
            guard cols > 0 else { return }
            let cellW = size.width / CGFloat(cols)
            let cellH = size.height / CGFloat(rows)

            for r in 0..<rows {
                for c in 0..<cols {
                    let v = grid[r][c]
                    if abs(v) < 0.1 { continue }
                    let color: Color = v > 0
                        ? Color(red: 0.72, green: 0.33, blue: 0.31, opacity: min(abs(v), 1.0))
                        : Color(red: 0.23, green: 0.43, blue: 0.65, opacity: min(abs(v), 1.0))
                    let rect = CGRect(x: CGFloat(c) * cellW, y: CGFloat(r) * cellH, width: cellW, height: cellH)
                    ctx.fill(Path(rect), with: .color(color))
                }
            }
        }
    }
}
