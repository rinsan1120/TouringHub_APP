package jp.touringhub.assistant;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.location.Location;
import android.location.LocationManager;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.List;

/** Foreground location only, rounded to an approximate grid before use; no background tracking. */
final class NearbyLocation implements AutoCloseable {
    private final Context context;
    private final Handler handler;
    private final LocationManager manager;
    private final List<CancellationSignal> signals = new ArrayList<>();
    private Runnable timeout;
    private int generation;
    NearbyLocation(Context context, Handler handler) {
        this.context = context;
        this.handler = handler;
        manager = context.getSystemService(LocationManager.class);
    }
    void get(Consumer<CandidateRanking.Origin> callback) {
        close();
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            callback.accept(null); return;
        }
        int request = generation;
        try {
            if (!manager.isLocationEnabled()) { callback.accept(null); return; }
            List<String> providers = new ArrayList<>();
            Location best = null;
            List<String> candidates = new ArrayList<>(List.of(LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER));
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                candidates.add(LocationManager.GPS_PROVIDER);
            }
            for (String candidate : candidates) {
                if (!manager.hasProvider(candidate) || !manager.isProviderEnabled(candidate)) continue;
                providers.add(candidate);
                Location cached = manager.getLastKnownLocation(candidate);
                if (recent(cached) && (best == null || cached.getElapsedRealtimeNanos() > best.getElapsedRealtimeNanos())) best = cached;
            }
            if (best != null) { callback.accept(origin(best)); return; }
            if (providers.isEmpty()) { callback.accept(null); return; }
            timeout = () -> complete(request, null, callback);
            handler.postDelayed(timeout, 6000);
            int[] remaining = {providers.size()};
            Consumer<Location> received = location -> {
                if (request != generation) return;
                if (recent(location)) complete(request, origin(location), callback);
                else if (--remaining[0] == 0) complete(request, null, callback);
            };
            // Some handsets expose a fused provider that does not yield a coarse fix.
            // Request available providers in parallel; cancel all when the first valid fix arrives.
            for (String provider : providers) {
                if (request != generation) break;
                CancellationSignal signal = new CancellationSignal();
                signals.add(signal);
                try { manager.getCurrentLocation(provider, signal, context.getMainExecutor(), received); }
                catch (SecurityException | IllegalArgumentException ex) { received.accept(null); }
            }
        } catch (SecurityException | IllegalArgumentException ex) {
            complete(request, null, callback);
        }
    }
    private boolean recent(Location location) {
        if (location == null) return false;
        long age = SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos();
        if ((context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Log.d("TouringHubLocation", "provider=" + location.getProvider() + " ageMs=" + age / 1000000
                    + " accuracyMeters=" + location.getAccuracy());
        }
        return age >= 0 && age <= 120_000_000_000L && location.hasAccuracy() && location.getAccuracy() <= 10000;
    }
    private static CandidateRanking.Origin origin(Location location) {
        return new CandidateRanking.Origin(location.getLatitude(), location.getLongitude()).approximate();
    }
    private void complete(int request, CandidateRanking.Origin location, Consumer<CandidateRanking.Origin> callback) {
        if (request != generation) return;
        close();
        callback.accept(location);
    }
    @Override public void close() {
        ++generation;
        if (timeout != null) handler.removeCallbacks(timeout);
        for (CancellationSignal signal : signals) signal.cancel();
        signals.clear();
    }
}
