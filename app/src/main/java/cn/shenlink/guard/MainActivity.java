package cn.shenlink.guard;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceResponse;
import android.util.Base64;
import android.widget.LinearLayout;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Offline, asset-only dashboard. The SIM monitor retains its existing preferences and service. */
public class MainActivity extends Activity {
    private WebView web;
    private String pendingJourney = "";
    private static final int EXPORT = 201, IMPORT = 202, LOCATION = 203, NOTIFY = 204, TIMETABLE_IMAGE = 205;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Notifications.channels(this); CalendarReminders.channel(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(246,247,244));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                android.graphics.Insets ime=insets.getInsets(WindowInsets.Type.ime());
                v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom));
            } else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        web=new WebView(this); web.setBackgroundColor(Color.rgb(246,247,244));
        WebSettings settings=web.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false); settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW); settings.setBlockNetworkLoads(true);
        settings.setTextZoom(100); web.addJavascriptInterface(new DashboardBridge(),"Android");
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){return assetResponse(request.getUrl());}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,String url){return assetResponse(Uri.parse(url));}
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}
            @Override public boolean shouldOverrideUrlLoading(WebView view,String url){return true;}
        });
        root.addView(web,new LinearLayout.LayoutParams(-1,-1));setContentView(root);root.requestApplyInsets();
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html"); CalendarReminders.sync(this);
    }
    private WebResourceResponse assetResponse(Uri uri){
        try {
            String path=uri.getPath();
            if(!"https".equals(uri.getScheme())||!"appassets.androidplatform.net".equals(uri.getHost())||path==null||!path.startsWith("/assets/")||path.contains("..")||path.contains("\\"))return deniedAsset();
            String name=path.substring(8),mime=name.endsWith(".js")?"application/javascript":name.endsWith(".css")?"text/css":name.endsWith(".html")?"text/html":name.endsWith(".json")?"application/json":name.endsWith(".wasm")?"application/wasm":"application/octet-stream";
            return new WebResourceResponse(mime,mime.startsWith("text/")||mime.contains("javascript")||mime.contains("json")?"UTF-8":null,getAssets().open(name));
        } catch(Exception ex){return deniedAsset();}
    }
    private WebResourceResponse deniedAsset(){return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",java.util.Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
    private void readTimetableImage(Uri uri){
        new Thread(()->{String stage="读取图片";try{
            byte[] encoded;try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(in==null)throw new Exception();byte[] chunk=new byte[8192];int n;while((n=in.read(chunk))!=-1){if(out.size()+n>20*1024*1024)throw new Exception("图片超过20MB");out.write(chunk,0,n);}encoded=out.toByteArray();}
            BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(encoded,0,encoded.length,options);if(options.outWidth<=0||options.outHeight<=0||(long)options.outWidth*options.outHeight>60000000)throw new Exception("图片格式或尺寸不支持");options.inJustDecodeBounds=false;options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>2600)options.inSampleSize*=2;
            Bitmap decoded=BitmapFactory.decodeByteArray(encoded,0,encoded.length,options);if(decoded==null)throw new Exception();
            int rotation=0;try{android.media.ExifInterface exif=new android.media.ExifInterface(new ByteArrayInputStream(encoded));int orientation=exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);rotation=orientation==6?90:orientation==3?180:orientation==8?270:0;}catch(Exception ignored){}
            if(rotation!=0){Matrix matrix=new Matrix();matrix.postRotate(rotation);Bitmap rotated=Bitmap.createBitmap(decoded,0,0,decoded.getWidth(),decoded.getHeight(),matrix,true);if(rotated!=decoded)decoded.recycle();decoded=rotated;}
            Bitmap white=Bitmap.createBitmap(decoded.getWidth(),decoded.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(white);canvas.drawColor(Color.WHITE);canvas.drawBitmap(decoded,0,0,null);decoded.recycle();ByteArrayOutputStream result=new ByteArrayOutputStream();white.compress(Bitmap.CompressFormat.JPEG,96,result);white.recycle();String data="data:image/jpeg;base64,"+Base64.encodeToString(result.toByteArray(),Base64.NO_WRAP);
            // Load the bundled engine explicitly before handing the image to the WebView.
            // evaluateJavascript avoids a missing/deferred external script leaving Tesseract undefined.
            stage="加载识别引擎";String engine;try(InputStream in=getAssets().open("vendor/ocr/tesseract.min.js");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] chunk=new byte[8192];int n;while((n=in.read(chunk))!=-1)out.write(chunk,0,n);engine=new String(out.toByteArray(),StandardCharsets.UTF_8);}
            final String engineScript=engine;
            runOnUiThread(()->{if(web!=null&&!isFinishing())web.evaluateJavascript(engineScript,ignored->{if(web!=null&&!isFinishing())web.evaluateJavascript("window.onTimetableImage("+JSONObject.quote(data)+")",null);});});
        }catch(Exception|OutOfMemoryError ex){final String detail=stage+"失败："+(ex instanceof OutOfMemoryError?"可用内存不足，请裁剪图片后重试":ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage());runOnUiThread(()->message(detail));}},"timetable-image").start();
    }
    private android.content.SharedPreferences dashboard(){return getSharedPreferences("linkday",MODE_PRIVATE);}
    private void message(String value){if(web!=null)web.evaluateJavascript("window.onNativeMessage && window.onNativeMessage("+JSONObject.quote(value)+")",null);}
    private void requestJourney(String id) {
        if(id==null||id.length()>100)return;
        try {JSONObject d=new JSONObject(dashboard().getString("data","{}"));JSONObject a=d.optJSONObject("active");if(a==null||!id.equals(a.optString("id"))){message("请先开始一段通勤");return;}}catch(Exception ex){message("没有进行中的通勤");return;}
        pendingJourney=id; List<String> permissions=new ArrayList<>();
        if(!PhoneState.allowed(this,Manifest.permission.ACCESS_FINE_LOCATION)){permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);}
        if(Build.VERSION.SDK_INT>=33&&!PhoneState.allowed(this,Manifest.permission.POST_NOTIFICATIONS))permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        if(!permissions.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("记录这次通勤的位置节点")
                .setMessage("仅在你开始定位后运行。用于识别接近口岸、上水和大学站的时间；不保存完整轨迹，不上传位置。通知栏可随时停止。交通方式与费用仍由你确认。")
                .setPositiveButton("继续",(d,w)->requestPermissions(permissions.toArray(new String[0]),LOCATION))
                .setNegativeButton("暂不定位",(d,w)->{pendingJourney="";message("可以继续手动记录这次通勤");}).show();
        } else startJourneyNow();
    }
    private void startJourneyNow(){
        if(pendingJourney.isEmpty())return;
        if(!PhoneState.allowed(this,Manifest.permission.ACCESS_COARSE_LOCATION)){message("位置未授权，仍可手动记录路线与费用");pendingJourney="";return;}
        try {startForegroundService(new Intent(this,JourneyService.class).setAction("start").putExtra("id",pendingJourney));message("已请求启动通勤定位，可在 App 内停止");}
        catch(RuntimeException ex){message("系统未允许启动定位，请在应用内重试");}
        pendingJourney="";
    }
    private void notifications(){
        if(Build.VERSION.SDK_INT>=33&&!PhoneState.allowed(this,Manifest.permission.POST_NOTIFICATIONS)){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFY);return;}
        if(!Notifications.manager(this).areNotificationsEnabled()){
            try{startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));}catch(RuntimeException ex){message("请在系统设置中开启通知");}
        }else{CalendarReminders.sync(this);message("通知权限已开启；请同时允许后台运行");}
    }
    public final class DashboardBridge {
        @JavascriptInterface public String getData(){return dashboard().getString("data","");}
        @JavascriptInterface public boolean saveData(String value){
            if(value==null||value.length()>4*1024*1024)return false;
            try{JSONObject d=new JSONObject(value);if(d.optInt("version")!=2||d.optJSONArray("courses")==null||d.optJSONArray("tasks")==null||d.optJSONArray("routes")==null||d.optJSONArray("trips")==null)return false;
                boolean ok=dashboard().edit().putString("data",value).commit();if(ok)CalendarReminders.sync(MainActivity.this);return ok;
            }catch(Exception ex){return false;}
        }
        @JavascriptInterface public String getState(){
            try{JSONObject result=new JSONObject();AppPrefs prefs=new AppPrefs(MainActivity.this);
                result.put("guardRunning",MonitorService.running);result.put("guardWanted",prefs.wanted());result.put("guardStatus",prefs.status());
                result.put("journey",JourneyService.snapshot(MainActivity.this));result.put("notifications",Notifications.manager(MainActivity.this).areNotificationsEnabled());return result.toString();
            }catch(Exception ex){return "{}";}
        }
        @JavascriptInterface public void openGuard(){runOnUiThread(()->startActivity(new Intent(MainActivity.this,GuardActivity.class)));}
        @JavascriptInterface public void startJourney(String id){runOnUiThread(()->requestJourney(id));}
        @JavascriptInterface public void stopJourney(){runOnUiThread(()->{JourneyService.markStopped(MainActivity.this);stopService(new Intent(MainActivity.this,JourneyService.class));message("通勤定位已停止");});}
        @JavascriptInterface public void requestNotifications(){runOnUiThread(()->notifications());}
        @JavascriptInterface public void exitApp(){runOnUiThread(()->finish());}
        @JavascriptInterface public void chooseTimetableImage(){runOnUiThread(()->{try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*").putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/png","image/jpeg","image/webp"}),TIMETABLE_IMAGE);}catch(RuntimeException ex){message("未找到图片选择程序");}});}
        @JavascriptInterface public void exportData(){runOnUiThread(()->{
            try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"通勤助手-"+LocalDate.now(ZoneOffset.ofHours(8))+".json"),EXPORT);}catch(RuntimeException ex){message("未找到可用的文件保存程序");}
        });}
        @JavascriptInterface public void importData(){runOnUiThread(()->{
            try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/plain","application/octet-stream"}),IMPORT);}catch(RuntimeException ex){message("未找到可用的文件选择程序");}
        });}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==LOCATION)startJourneyNow();if(request==NOTIFY){CalendarReminders.sync(this);message(Notifications.manager(this).areNotificationsEnabled()?"通知已开启":"通知尚未开启，日程仍会保存在应用里");}}
    @Override protected void onActivityResult(int request,int result,Intent intent){
        super.onActivityResult(request,result,intent);if(result!=RESULT_OK||intent==null||intent.getData()==null)return;Uri uri=intent.getData();
        if(request==TIMETABLE_IMAGE){readTimetableImage(uri);return;}
        try{
            if(request==EXPORT){try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new Exception();out.write(dashboard().getString("data","{}").getBytes(StandardCharsets.UTF_8));}message("备份已导出");}
            if(request==IMPORT){String raw;try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(in==null)throw new Exception();byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>4*1024*1024)throw new Exception("文件过大");out.write(buf,0,n);}raw=new String(out.toByteArray(),StandardCharsets.UTF_8);}web.evaluateJavascript("window.onNativeImport("+JSONObject.quote(raw)+")",null);}
        }catch(Exception ex){message("文件处理失败，请选择有效的备份文件或其他保存位置");}
    }
    @Override public void onBackPressed(){if(web!=null)web.evaluateJavascript("window.onNativeBack && window.onNativeBack()",null);else super.onBackPressed();}
    @Override protected void onDestroy(){if(web!=null){web.removeJavascriptInterface("Android");web.destroy();web=null;}super.onDestroy();}
}
