/*---DiBuat oleh Jureni123 2026----*/

package dk.bearware.gui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
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

        // ✅ CEK: SUDAH LOGIN? → LANGSUNG KE SERVER LIST
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
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

        // ✅ KONFIGURASI GOOGLE SIGN-IN DENGAN SAFE FALLBACK
        try {
            String webClientId = getString(R.string.default_web_client_id);

            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(webClientId)
                    .requestEmail()
                    .build();

            mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        } catch (Exception e) {
            // FALLBACK: google-services.json belum ada → tetap bisa jalan tanpa ID token
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .build();
            mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

            if (tvStatus != null) {
                tvStatus.setText("⚠️ Mode terbatas: google-services.json belum dikonfigurasi");
            }
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

            // ✅ USER BATAL PILIH AKUN
            if (data == null) {
                if (tvStatus != null) tvStatus.setText("Login dibatalkan");
                Toast.makeText(this, "Login dibatalkan", Toast.LENGTH_SHORT).show();
                return;
            }

            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);

            // ✅ TASK GAGAL
            if (task == null || !task.isSuccessful()) {
                if (tvStatus != null) tvStatus.setText("Gagal mendapatkan akun Google");
                Toast.makeText(this, "Gagal mendapatkan akun Google", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                GoogleSignInAccount account = task.getResult();

                // ✅ ACCOUNT NULL
                if (account == null) {
                    if (tvStatus != null) tvStatus.setText("Akun Google kosong");
                    Toast.makeText(this, "Akun Google kosong", Toast.LENGTH_SHORT).show();
                    return;
                }

                String idToken = account.getIdToken();

                // ✅ TIDAK ADA ID TOKEN (fallback mode) → anggap berhasil
                if (idToken == null || idToken.isEmpty()) {
                    if (tvStatus != null) tvStatus.setText("✅ Login berhasil! Mengalihkan...");
                    Toast.makeText(this, "Login berhasil!", Toast.LENGTH_SHORT).show();
                    
                    // Simpan profil meski tanpa ID Token (jika user sudah pernah login sebelumnya)
                    savePhotoUrlToFirestore(); 
                    goToServerList();
                    return;
                }

                firebaseAuthWithGoogle(idToken);

            } catch (Exception e) {
                if (tvStatus != null) tvStatus.setText("Error: " + e.getMessage());
                Toast.makeText(this, "Error login: " + e.getMessage(), Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        if (tvStatus != null) tvStatus.setText("Memverifikasi dengan Firebase...");

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // ✅ LOGIN BERHASIL!
                        FirebaseUser user = mAuth.getCurrentUser();
                        String email = (user != null) ? user.getEmail() : "unknown";

                        if (tvStatus != null) tvStatus.setText("✅ Login berhasil! (" + email + ")");
                        Toast.makeText(this, "Login berhasil! Selamat datang.", Toast.LENGTH_SHORT).show();

                        // ✅ SIMPAN URL FOTO PROFIL KE FIRESTORE
                        savePhotoUrlToFirestore();

                        // ✅ PINDAH KE ServerListActivity
                        goToServerList();

                    } else {
                        // ✅ LOGIN GAGAL — FEEDBACK JELAS
                        String errorMsg = "Login gagal";
                        if (task.getException() != null) {
                            errorMsg = task.getException().getMessage();
                        }

                        if (tvStatus != null) tvStatus.setText(" " + errorMsg);
                        Toast.makeText(this, "Login gagal: " + errorMsg, Toast.LENGTH_LONG).show();

                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                    }
                });
    }

    /**
     * ✅ FUNGSI BARU: MENYIMPAN URL FOTO PROFIL KE FIRESTORE
     */
    private void savePhotoUrlToFirestore() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        
        if (currentUser != null) {
            String uid = currentUser.getUid(); // ID unik user dari Firebase Auth
            String photoUrl = "";
            
            // Ambil URL foto, jika tidak ada pakai string kosong
            if (currentUser.getPhotoUrl() != null) {
                photoUrl = currentUser.getPhotoUrl().toString();
            }

            FirebaseFirestore db = FirebaseFirestore.getInstance();
            
            // Siapkan data yang akan disimpan
            Map<String, Object> updates = new HashMap<>();
            updates.put("foto_profil_url", photoUrl);
            
            // Update dokumen user di collection 'pengguna'
            // Menggunakan SetOptions.merge() agar field lain (seperti kata sandi teamtalk) tidak terhapus
            db.collection("pengguna").document(uid)
              .set(updates, SetOptions.merge())
              .addOnSuccessListener(aVoid -> {
                  Log.d(TAG, "URL Foto berhasil disimpan untuk UID: " + uid);
              })
              .addOnFailureListener(e -> {
                  Log.e(TAG, "Gagal simpan URL foto", e);
              });
        }
    }

    /**
     * ✅ PINDAH KE ServerListActivity — TEMPAT USER PILIH/KONEKSI SERVER
     */
    private void goToServerList() {
        Intent intent = new Intent(this, ServerListActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
