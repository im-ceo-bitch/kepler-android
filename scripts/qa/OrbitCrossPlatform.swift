import Foundation

// Compile together with the actual iOS geometry source (no SwiftUI required).
@main struct OrbitCrossPlatform {
    static func main() {
        typealias G = IumrahOrbitV5Geometry
        func emit(_ values: Double...) { print(values.map { String($0) }.joined(separator: ",")) }
        for sample in 0..<101 {
            let time = Double(sample) * 0.247 + 0.013
            let rotation = G.rotation(time, duration: 25)
            let r = G.reveal(time: time, rotation: rotation, duration: 3.2)
            let p = G.pinnedReveal(rotation: rotation, expansion: G.ease(time / 0.55))
            emit(rotation, Double(r.slot), Double(r.dot), r.expansion, r.symbolOpacity, r.center.x, r.center.y, r.radius)
            emit(Double(p.dot), p.expansion, p.symbolOpacity, p.center.x, p.center.y, p.radius)
            for row in G.radii.indices {
                for dot in 0..<G.pointsPerRing {
                    let point = G.point(row: row, dot: dot, rotation: rotation)
                    emit(point.x, point.y, point.angle, G.visibility(row: row, dot: dot, point: point, reveal: r), G.huePosition(point.angle))
                }
            }
            for index in 0..<12 {
                let point = G.assemblyPoint(index: index, count: 12, rotation: rotation, progress: G.ease(Double(sample) / 100))
                emit(point.x, point.y, point.angle)
            }
        }
    }
}
