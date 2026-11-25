import Foundation
let dates = ["2026-02-18", "2026-03-19", "2026-03-29", "2026-06-21", "2026-10-25", "2026-12-21", "2027-02-09"]
for p in IumrahPrayerPlace.presets {
 let df = DateFormatter(); df.locale = Locale(identifier:"en_US_POSIX"); df.timeZone = p.timeZone; df.dateFormat = "yyyy-MM-dd HH:mm"
 for d in dates { for m in IumrahPrayerMethod.allCases { for h in [false,true] {
 let s = IumrahPrayerCalculator.calculate(on: df.date(from:d+" 12:00")!, place:p, method:m, hanafi:h)
 for k in IumrahPrayerKind.allCases { if let t=s.date(for:k) { print("\(p.id)|\(d)|\(IumrahPrayerMethod.allCases.firstIndex(of:m)!)|\(h)|\(k.rawValue)|\(t.timeIntervalSince1970)") } }
 } } }
}
