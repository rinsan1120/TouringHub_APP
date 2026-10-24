package jp.touringhub.assistant;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;

public final class Navigator {
    public static final String GOOGLE = "com.google.android.apps.maps";
    public static final String MOVILINK = "jp.co.toyotaconnected.tlbsapp";
    private Navigator() {}
    public static Intent intent(Destination destination, boolean movilink) {
        if (movilink) {
            String url = new Uri.Builder().scheme("https").authority("www.google.com")
                    .path("maps/search/").appendQueryParameter("api", "1")
                    .appendQueryParameter("query", destination.coordinates()).build().toString();
            // moviLink documents receiving shared facilities, but does not publish a direct navigation API.
            return new Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(MOVILINK)
                    .putExtra(Intent.EXTRA_SUBJECT, destination.name())
                    .putExtra(Intent.EXTRA_TEXT, destination.spokenLabel() + "\n" + url);
        }
        Uri url = new Uri.Builder().scheme("https").authority("www.google.com")
                .path("maps/dir/").appendQueryParameter("api", "1")
                .appendQueryParameter("destination", destination.coordinates())
                .appendQueryParameter("travelmode", "driving")
                .appendQueryParameter("dir_action", "navigate").build();
        return new Intent(Intent.ACTION_VIEW, url).setPackage(GOOGLE);
    }
    public static boolean available(Activity activity, boolean movilink) {
        Destination dummy = new Destination("確認用", "東京", 35.68, 139.76);
        return intent(dummy, movilink).resolveActivity(activity.getPackageManager()) != null;
    }
    public static boolean launch(Activity activity, Destination destination, boolean movilink) {
        try {
            activity.startActivity(intent(destination, movilink));
            return true;
        } catch (ActivityNotFoundException | SecurityException ex) {
            return false;
        }
    }
}
