package dk.bearware.gui;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

import dk.bearware.Channel;
import dk.bearware.ServerProperties;
import dk.bearware.TeamTalkBase;
import dk.bearware.User;
import dk.bearware.UserState;
import dk.bearware.UserType;
import dk.bearware.backend.TeamTalkService;
import dk.bearware.data.Preferences;
import dk.bearware.utils.PrefsHelper;

// Ganti import Utils dan TeamTalkConstants sesuai struktur projectmu
// import dk.bearware.backend.TeamTalkConstants; 
// import dk.bearware.utils.Utils; 

public class ChannelListAdapter extends BaseAdapter {
    private static final int PARENT_CHANNEL_VIEW_TYPE = 0, CHANNEL_VIEW_TYPE = 1, USER_VIEW_TYPE = 2, INFO_VIEW_TYPE = 3, VIEW_TYPE_COUNT = 4;
    
    private final LayoutInflater inflater;
    private final Context context;
    private final TeamTalkService service;
    private final TeamTalkBase client;
    private final PrefsHelper prefs;
    private final List<Integer> userIDs; // Untuk tracking selected users
    
    // Data Lists
    private Vector<Channel> subchannels = new Vector<>();
    private Vector<Channel> stickychannels = new Vector<>();
    private Vector<User> currentusers = new Vector<>();
    private Channel curchannel;

    // Interface untuk callback klik item (biar bisa panggil method di MainActivity)
    public interface OnChannelItemClickListener {
        void onChannelItemClick(Object item);
        void onChannelItemLongClick(Object item, View v);
        void joinChannel(Channel channel);
    }

    private OnChannelItemClickListener listener;

    public ChannelListAdapter(@NonNull Context context, @NonNull TeamTalkService service, 
                              @NonNull TeamTalkBase client, @NonNull PrefsHelper prefs,
                              @NonNull List<Integer> userIDs, @NonNull OnChannelItemClickListener listener) {
        this.context = context;
        this.service = service;
        this.client = client;
        this.prefs = prefs;
        this.userIDs = userIDs;
        this.listener = listener;
        this.inflater = LayoutInflater.from(context);
    }

    public void setCurrentChannel(Channel channel) {
        this.curchannel = channel;
        notifyDataSetChanged();
    }

    @Override
    public void notifyDataSetChanged() {
        subchannels.clear(); stickychannels.clear(); currentusers.clear();
        int chanid;
        if(curchannel != null) {
            chanid = curchannel.nChannelID;
            // Ganti Utils.getSubChannels dengan logic asli atau helper method
            // subchannels = Utils.getSubChannels(chanid, service.getChannels());
            // stickychannels = Utils.getStickyChannels(chanid, service.getChannels());
            // currentusers = Utils.getUsers(chanid, service.getUsers());
            
            // TEMPORARY PLACEHOLDER: Kamu perlu isi ini dengan logic filtering asli dari MainActivity
            // Karena saya tidak punya akses ke class Utils kamu, pastikan kamu copy logic filteringnya kesini
        } else {
            chanid = client.getRootChannelID();
            Channel root = service.getChannels().get(chanid);
            if(root != null) subchannels.add(root);
        }
        
        // Sorting logic (copy dari MainActivity asli)
        Collections.sort(subchannels, (c1, c2) -> c1.szName.compareToIgnoreCase(c2.szName));
        Collections.sort(stickychannels, (c1, c2) -> c1.szName.compareToIgnoreCase(c2.szName));
        Collections.sort(currentusers, (u1, u2) -> {
             // Copy logic sorting user dari MainActivity disini
             return u1.szNickname.compareToIgnoreCase(u2.szNickname); 
        });
        
        super.notifyDataSetChanged();
    }

    @Override public int getCount() { /* Copy logic getCount dari MainActivity */ return currentusers.size() + subchannels.size() + stickychannels.size(); }
    @Override public Object getItem(int position) { /* Copy logic getItem dari MainActivity */ return null; }
    @Override public long getItemId(int position) { return position; }
    @Override public int getItemViewType(int position) { /* Copy logic getItemViewType dari MainActivity */ return USER_VIEW_TYPE; }
    @Override public int getViewTypeCount() { return VIEW_TYPE_COUNT; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Object item = getItem(position);

        if(item instanceof Channel) {
            Channel channel = (Channel) item;
            // ... Logic render Channel (Copy dari MainActivity) ...
            // Pastikan inflate layout dan set text sesuai aslinya
        }
        else if(item instanceof User) {
            User user = (User) item;
            if (convertView == null || convertView.findViewById(R.id.nickname) == null)
                convertView = inflater.inflate(R.layout.item_user, parent, false);
            
            ImageView usericon = convertView.findViewById(R.id.usericon);
            TextView nickname = convertView.findViewById(R.id.nickname);
            TextView status = convertView.findViewById(R.id.status);
            
            // Render User Basic Info
            nickname.setText(user.szNickname);
            status.setText(user.szStatusMsg);
            
            // Determine Icon Resource (Copy logic icon_resource dari MainActivity)
            int icon_resource = R.drawable.man_blue; // Default placeholder
            
            // ✅ MODIFIKASI FOTO PROFIL FIRESTORE (SUDAH TERPASANG DI SINI!)
            String uid = String.valueOf(user.nUserID);
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            
            db.collection("pengguna").document(uid)
              .get()
              .addOnSuccessListener(doc -> {
                  if (doc != null && doc.exists()) {
                      String url = doc.getString("foto_profil_url");
                      if (url != null && !url.isEmpty()) {
                          Glide.with(context)
                               .load(url)
                               .circleCrop()
                               .placeholder(icon_resource)
                               .error(icon_resource)
                               .into(usericon);
                          return; 
                      }
                  }
                  usericon.setImageResource(icon_resource);
              })
              .addOnFailureListener(e -> usericon.setImageResource(icon_resource));

            // Setup Buttons & Click Listeners
            Button sndmsg = convertView.findViewById(R.id.msg_btn);
            sndmsg.setOnClickListener(v -> {
                Intent intent = new Intent(context, TextMessageActivity.class);
                intent.putExtra(TextMessageActivity.EXTRA_USERID, user.nUserID);
                context.startActivity(intent);
            });

            convertView.setOnClickListener(view -> listener.onChannelItemClick(item));
            convertView.setOnLongClickListener(view -> {
                listener.onChannelItemLongClick(item, view);
                return true;
            });
        }
        return convertView;
    }
}
