/*---DiBuat oleh Jureni123 2026----*/

package dk.bearware.gui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class SplashActivity extends AppCompatActivity {
    private static final int RC_SIGN_IN = 9001;
    private static final String TAG = "SplashActivity";

    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseAuth mAuth;
    private ProgressBar progressBar;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseAuth.getInstance();

        // ✅ CEK: SUDAH LOGIN?
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // PENTING: Cek & Update Foto Profil setiap kali app dibuka
            // Ini menjamin jika user ganti foto di Google, database ikut terupdate
            savePhotoUrlToFirestore(); 
            
            goToServerList();
            return;
        }

        // BELUM LOGIN → TAMPIL LAYAR LOGIN
        setContentView(R.layout.activity_splash);
        progressBar = findViewById(R.id.progress_bar);
        tvStatus = findViewById(R.id.tv_status);

        if (tvStatus != null) {
            tvStatus.setText("Silakan login untuk melanjutkan");
        }

        // ✅ KONFIGURASI GOOGLE SIGN-IN
        try {
            String webClientId = getString(R.string.default_web_client_id);
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(webClientId)
                    .requestEmail()
                    .build();
            mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        } catch (Exception e) {
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .build();
            mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
            if (tvStatus != null) tvStatus.setText("⚠️ Mode terbatas");
        }

        SignInButton signInButton = findViewById(R.id.sign_in_button);
        if (signInButton != null) {
            signInButton.setSize(SignInButton.SIZE_WIDE);
            signInButton.setOnClickListener(v -> signIn());
        }
    }

    private void signIn() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (tvStatus != null) tvStatus.setText("Membuka Google Sign-In...");
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            if (progressBar != null) progressBar.setVisibility(View.GONE);

            if (data == null) {
                Toast.makeText(this, "Login dibatalkan", Toast.LENGTH_SHORT).show();
                return;
            }

            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            if (task == null || !task.isSuccessful()) {
                Toast.makeText(this, "Gagal mendapatkan akun Google", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                GoogleSignInAccount account = task.getResult();
                if (account == null) {
                    Toast.makeText(this, "Akun Google kosong", Toast.LENGTH_SHORT).show();
                    return;
                }

                String idToken = account.getIdToken();
                if (idToken == null || idToken.isEmpty()) {
                    // Fallback mode
                    savePhotoUrlToFirestore(); 
                    goToServerList();
                    return;
                }

                firebaseAuthWithGoogle(idToken);

            } catch (Exception e) {
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        if (tvStatus != null) tvStatus.setText("Memverifikasi dengan Firebase...");

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        String email = (user != null) ? user.getEmail() : "unknown";
                        
                        if (tvStatus != null) tvStatus.setText("✅ Login berhasil! (" + email + ")");
                        Toast.makeText(this, "Login berhasil! Selamat datang.", Toast.LENGTH_SHORT).show();

                        // ✅ SIMPAN URL FOTO PROFIL KE FIRESTORE SAAT LOGIN BERHASIL
                        savePhotoUrlToFirestore();

                        goToServerList();

                    } else {
                        String errorMsg = "Login gagal";
                        if (task.getException() != null) errorMsg = task.getException().getMessage();
                        if (tvStatus != null) tvStatus.setText(" " + errorMsg);
                        Toast.makeText(this, "Login gagal: " + errorMsg, Toast.LENGTH_LONG).show();
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                    }
                });
    }

    /**
     * ✅ FUNGSI UTAMA: MENYIMPAN/UPDATE FOTO PROFIL
     * - Dipanggil saat Login Baru
     * - Dipanggil saat App Dibuka (untuk cek perubahan foto)
     */
    private void savePhotoUrlToFirestore() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        
        if (currentUser != null) {
            String uid = currentUser.getUid(); 
            
            // Cek apakah foto tersedia
            if (currentUser.getPhotoUrl() != null) {
                String photoUrl = currentUser.getPhotoUrl().toString();
                
                FirebaseFirestore db = FirebaseFirestore.getInstance();
                Map<String, Object> updates = new HashMap<>();
                updates.put("foto_profil_url", photoUrl);
                
                // Gunakan merge agar field lain (password server, dll) tidak hilang
                db.collection("pengguna").document(uid)
                  .set(updates, SetOptions.merge())
                  .addOnSuccessListener(aVoid -> {
                      Log.d(TAG, "Foto profil di-update untuk UID: " + uid);
                      // Opsional: Toast sukses (bisa dimatikan kalau ganggu)
                      // Toast.makeText(SplashActivity.this, "Profil diperbarui", Toast.LENGTH_SHORT).show();
                  })
                  .addOnFailureListener(e -> {
                      Log.e(TAG, "Gagal update foto", e);
                  });
            } else {
                // Jika foto null, coba lagi dalam 3 detik (handling delay Google Auth)
                Log.d(TAG, "Foto belum siap, retry dalam 3 detik...");
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    savePhotoUrlToFirestore();
                }, 3000);
            }
        }
    }

    private void goToServerList() {
        Intent intent = new Intent(this, ServerListActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
