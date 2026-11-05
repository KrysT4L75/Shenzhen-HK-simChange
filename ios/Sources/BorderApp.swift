import SwiftUI
import CoreLocation
import UserNotifications

@main struct BorderApp: App {
    @StateObject private var guardModel = GuardModel()
    var body: some Scene { WindowGroup { HomeView().environmentObject(guardModel) } }
}
struct Port: Identifiable {
    let id: Int; let name: String; let lat: Double; let lon: Double
    static let all = [Port(id:0,name:"罗湖",lat:22.5319,lon:114.1144),Port(id:1,name:"福田／落马洲支线",lat:22.515,lon:114.065),Port(id:2,name:"深圳湾",lat:22.502,lon:113.944),Port(id:3,name:"皇岗",lat:22.519,lon:114.073),Port(id:4,name:"莲塘／香园围",lat:22.554,lon:114.154),Port(id:5,name:"文锦渡",lat:22.537,lon:114.129),Port(id:6,name:"沙头角",lat:22.550,lon:114.223)]
}
@MainActor final class GuardModel: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var enabled = false
    @Published var status = "出发前开启口岸提醒"
    @Published var selected = UserDefaults.standard.integer(forKey:"port")
    private let location = CLLocationManager()
    override init() { super.init(); location.delegate = self; reconcile() }
    func reconcile() {
        UNUserNotificationCenter.current().getPendingNotificationRequests { requests in
            Task { @MainActor in
                self.enabled = requests.contains { $0.identifier == "port-reminder" }
                self.status = self.enabled ? "口岸位置提醒已安排" : "出发前开启口岸提醒"
                if self.location.authorizationStatus == .denied || self.location.authorizationStatus == .restricted { self.status = "位置权限不可用，请检查设置" }
            }
        }
    }
    func start() {
        location.requestWhenInUseAuthorization()
        UNUserNotificationCenter.current().requestAuthorization(options:[.alert,.sound,.badge]) { granted,error in
            Task { @MainActor in
                guard granted else { self.status = "请在设置中允许通知"; return }
                self.scheduleIfAllowed()
            }
        }
    }
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if manager.authorizationStatus == .denied { status = "请允许位置权限" }
    }
    func scheduleIfAllowed() {
        guard location.authorizationStatus == .authorizedWhenInUse || location.authorizationStatus == .authorizedAlways else { status = "授权位置后，请再次点开启"; return }
        let p = Port.all[min(max(selected,0),Port.all.count-1)]
        UserDefaults.standard.set(p.id,forKey:"port")
        let region = CLCircularRegion(center:CLLocationCoordinate2D(latitude:p.lat,longitude:p.lon),radius:1200,identifier:"port-reminder")
        region.notifyOnEntry = true; region.notifyOnExit = false
        let content = UNMutableNotificationContent()
        content.title = "接近\(p.name)，记得检查上网卡"
        content.body = "过关后请在设置 → 蜂窝网络中确认蜂窝数据用卡。接近口岸不代表已经过关。"
        content.sound = .default
        let trigger = UNLocationNotificationTrigger(region:region,repeats:true)
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier:"port-reminder",content:content,trigger:trigger)) { error in
            Task { @MainActor in
                if let error = error { self.status = error.localizedDescription }
                else { self.enabled = true; self.status = "\(p.name)附近的位置提醒已安排" }
            }
        }
    }
    func stop() { UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers:["port-reminder"]); enabled=false; status="提醒已停止" }
}
struct HomeView: View {
    @EnvironmentObject var model: GuardModel
    @Environment(\.scenePhase) private var scenePhase
    @State private var settings = false
    let green = Color(red:0.07,green:0.32,blue:0.26)
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment:.leading,spacing:20) {
                    Text("深圳 ⇄ 香港").font(.subheadline).foregroundStyle(.secondary)
                    VStack(alignment:.leading,spacing:22) {
                        HStack { Text("跨境通勤"); Spacer(); Image(systemName:"simcard") }.font(.subheadline)
                        Text(model.enabled ? "提醒已安排" : "准备好出发了吗").font(.title2.bold())
                        Text(model.status).font(.subheadline)
                        Button { model.enabled ? model.stop() : model.start() } label: {
                            Text(model.enabled ? "停止提醒" : "开启口岸提醒").font(.headline).frame(maxWidth:.infinity).padding(16)
                        }.background(Color(red:0.86,green:0.94,blue:0.65),in:RoundedRectangle(cornerRadius:18,style:.continuous)).foregroundStyle(green)
                    }.padding(24).foregroundStyle(.white).background(green,in:RoundedRectangle(cornerRadius:28,style:.continuous))
                    VStack(alignment:.leading,spacing:12) {
                        Label("过关后，确认蜂窝数据用卡",systemImage:"simcard.2").font(.headline)
                        Text("打开 iPhone 设置 → 蜂窝网络 → 蜂窝数据，选择对应的卡。").font(.subheadline).foregroundStyle(.secondary)
                        Text("iOS 版提供口岸位置提醒，不能可靠识别当前上网卡或替你切卡。").font(.caption).foregroundStyle(.secondary)
                    }.padding(22).background(.white,in:RoundedRectangle(cornerRadius:24,style:.continuous))
                }.padding(20)
            }.background(Color(red:0.96,green:0.97,blue:0.95))
            .navigationTitle("过关换卡")
            .toolbar { Button { settings=true } label: {Image(systemName:"slider.horizontal.3")} }
            .sheet(isPresented:$settings) {
                NavigationStack { Form {
                    Picker("常用口岸",selection:$model.selected) { ForEach(Port.all) { Text($0.name).tag($0.id) } }
                    Text("参考范围约1.2公里。位置参考点需实地校准，不用于判断国界或口岸开放状态。")
                    Button("应用口岸设置") { model.scheduleIfAllowed(); settings=false }
                    Section("提醒与隐私") {
                        Text("无账号，无位置上传。提醒可能受定位、系统省电和通知设置影响，不能保证在收费前到达。强制退出应用后请重新检查提醒。")
                        Button("打开应用权限设置") { if let url=URL(string:UIApplication.openSettingsURLString) {UIApplication.shared.open(url)} }
                    }
                }.navigationTitle("通勤设置").toolbar { Button("完成") {settings=false} } }
            }
            .onChange(of:scenePhase) { phase in if phase == .active {model.reconcile()} }
        }
    }
}
