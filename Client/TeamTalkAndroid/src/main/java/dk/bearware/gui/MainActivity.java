/*
 * Copyright (c) 2005-2018, BearWare.dk
 * 
 * Contact Information:
 *
 * Bjoern D. Rasmussen
 * Kirketoften 5
 * DK-8260 Viby J
 * Denmark
 * Email: contact@bearware.dk
 * Phone: +45 20 20 54 59
 * Web: http://www.bearware.dk
 *
 * This source code is part of the TeamTalk SDK owned by
 * BearWare.dk. Use of this file, or its compiled unit, requires a
 * TeamTalk SDK License Key issued by BearWare.dk.
 *
 * The TeamTalk SDK License Agreement along with its Terms and
 * Conditions are outlined in the file License.txt included with
 * the TeamTalk SDK distribution.
 */

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

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.FirebaseFirestore;

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
import dk.bearware.backend.TeamTalkConstants;
import dk.bearware.backend.TeamTalkService;
import dk.bearware.data.Preferences;
import dk.bearware.utils.PrefsHelper;
import dk.bearware.utils.Utils;

public class ChannelListAdapter extends BaseAdapter {
    private static final int PARENT_CHANNEL_VIEW_TYPE = 0, CHANNEL_VIEW_TYPE = 1, USER_VIEW_TYPE = 2, INFO_VIEW_TYPE = 3, VIEW_TYPE_COUNT = 4;
    
    private final LayoutInflater inflater;
    private final Context context;
    private final TeamTalkService service;
    private final TeamTalkBase client;
    private final PrefsHelper prefs;
    private final List<Integer> userIDs; 
    
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
            subchannels = Utils.getSubChannels(chanid, service.getChannels());
            stickychannels = Utils.getStickyChannels(chanid, service.getChannels());
            currentusers = Utils.getUsers(chanid, service.getUsers());
        } else {
            chanid = client.getRootChannelID();
            Channel root = service.getChannels().get(chanid);
            if(root != null) subchannels.add(root);
        }
        
        Collections.sort(subchannels, (c1, c2) -> c1.szName.compareToIgnoreCase(c2.szName));
        Collections.sort(stickychannels, (c1, c2) -> c1.szName.compareToIgnoreCase(c2.szName));
        Collections.sort(currentusers, (u1, u2) -> {
             if (prefs.get("movetalk_checkbox", true)) {
                if (((u1.uUserState & UserState.USERSTATE_VOICE) != 0) && ((u2.uUserState & UserState.USERSTATE_VOICE) == 0)) return -1;
                else if (((u1.uUserState & UserState.USERSTATE_VOICE) == 0) && ((u2.uUserState & UserState.USERSTATE_VOICE) != 0)) return 1;
            }
            String name1 = Utils.getDisplayName(context, u1);
            String name2 = Utils.getDisplayName(context, u2);
            return name1.compareToIgnoreCase(name2);
        });
        
        super.notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        int count = currentusers.size() + subchannels.size() + stickychannels.size();
        if ((curchannel != null) && (curchannel.nParentID > 0)) count++;
        return count;
    }

    @Override
    public Object getItem(int position) {
        if (position < stickychannels.size()) return stickychannels.get(position);
        position -= stickychannels.size();
        if (position < currentusers.size()) return currentusers.get(position);
        position -= currentusers.size();
        if ((curchannel != null) && (curchannel.nParentID > 0)) {
            if(position == 0) {
                Channel parent = service.getChannels().get(curchannel.nParentID);
                if(parent != null) return parent;
                return new Channel();
            }
            position--;
        }
        return subchannels.get(position);
    }

    @Override public long getItemId(int position) { return position; }

    @Override
    public int getItemViewType(int position) {
        if (position < stickychannels.size()) return INFO_VIEW_TYPE;
        position -= stickychannels.size();
        if (position < currentusers.size()) return USER_VIEW_TYPE;
        position -= currentusers.size();
        if ((curchannel != null) && (curchannel.nParentID > 0)) {
            if (position == 0) return PARENT_CHANNEL_VIEW_TYPE;
            position--;
        }
        return CHANNEL_VIEW_TYPE;
    }

    @Override public int getViewTypeCount() { return VIEW_TYPE_COUNT; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Object item = getItem(position);

        if(item instanceof Channel) {
            Channel channel = (Channel) item;
            switch (getItemViewType(position)) {
                case PARENT_CHANNEL_VIEW_TYPE :
                    if (convertView == null || convertView.findViewById(R.id.parentname) == null)
                        convertView = inflater.inflate(R.layout.item_channel_back, parent, false);
                    break;
                case CHANNEL_VIEW_TYPE :
                    if (convertView == null || convertView.findViewById(R.id.channelname) == null)
                        convertView = inflater.inflate(R.layout.item_channel, parent, false);
                    
                    ImageView chanicon = convertView.findViewById(R.id.channelicon);
                    TextView name = convertView.findViewById(R.id.channelname);
                    TextView topic = convertView.findViewById(R.id.chantopic);
                    Button join = convertView.findViewById(R.id.join_btn);
                    int icon_resource = R.drawable.channel_orange;
                    
                    if(channel.bPassword) {
                        icon_resource = R.drawable.channel_pink;
                        chanicon.setContentDescription(context.getString(R.string.text_passwdprot));
                        chanicon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
                    } else {
                        chanicon.setContentDescription(null);
                        chanicon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                    }
                    chanicon.setImageResource(icon_resource);
                    
                    if(channel.nParentID == 0) {
                        ServerProperties srvprop = new ServerProperties();
                        client.getServerProperties(srvprop);
                        name.setText(srvprop.szServerName);
                    } else name.setText(channel.szName);
                    topic.setText(channel.szTopic);
                    
                    View.OnClickListener listener = v -> { if (v.getId() == R.id.join_btn) this.listener.joinChannel(channel); };
                    join.setOnClickListener(listener);
                    join.setAccessibilityDelegate(null); // Accessibility handled by parent click if needed
                    join.setEnabled(channel.nChannelID != client.getMyChannelID());
                    
                    if (channel.nMaxUsers > 0) {
                        int population = Utils.getUsers(channel.nChannelID, service.getUsers()).size();
                        ((TextView)convertView.findViewById(R.id.population)).setText((population > 0) ? String.format(Locale.ROOT, "(%d)", population) : "");
                    }
                    break;
                case INFO_VIEW_TYPE :
                    if (convertView == null || convertView.findViewById(R.id.titletext) == null)
                        convertView = inflater.inflate(R.layout.item_info, parent, false);
                    TextView title = convertView.findViewById(R.id.titletext);
                    TextView details = convertView.findViewById(R.id.infodetails);
                    title.setText(channel.szName);
                    details.setText(channel.szTopic);
                    break;
            }
        }
        else if(item instanceof User) {
            User user = (User) item;
            if (convertView == null || convertView.findViewById(R.id.nickname) == null)
                convertView = inflater.inflate(R.layout.item_user, parent, false);
            
            ImageView usericon = convertView.findViewById(R.id.usericon);
            TextView nickname = convertView.findViewById(R.id.nickname);
            TextView status = convertView.findViewById(R.id.status);
            
            String name = Utils.getDisplayName(context, user);
            nickname.setText(name);
            status.setText(user.szStatusMsg);
            
            boolean selected = userIDs.contains(user.nUserID);
            boolean isOperator = client.isChannelOperator(user.nUserID, user.nChannelID);
            boolean isAdmin = (user.uUserType & UserType.USERTYPE_ADMIN) != 0;
            boolean talking = (user.uUserState & UserState.USERSTATE_VOICE) != 0;
            boolean female = (user.nStatusMode & TeamTalkConstants.STATUSMODE_FEMALE) != 0;
            boolean neutral = (user.nStatusMode & TeamTalkConstants.STATUSMODE_NEUTRAL) != 0;
            boolean away = (user.nStatusMode & TeamTalkConstants.STATUSMODE_AWAY) != 0;
            int icon_resource;
            
            if(user.nUserID == client.getMyUserID()) talking = service.isVoiceTransmitting();

            String move = selected ? context.getString(R.string.user_state_selected) : "";
            String speaking = talking ? context.getString(R.string.user_state_now_speaking, name) : name;
            String gender = female ? " 👩 " : neutral ? "  " : " 👨 ";
            String op = isOperator ? context.getString(R.string.user_state_operator) : "";
            String admin = isAdmin ? context.getString(R.string.user_state_admin) : "";
            nickname.setContentDescription(move + " " + speaking + gender + op + " " + admin);

            if (talking) {
                icon_resource = female ? R.drawable.woman_green : R.drawable.man_green;
            } else {
                if (female) icon_resource = away ? R.drawable.woman_orange : R.drawable.woman_blue;
                else icon_resource = away ? R.drawable.man_orange : R.drawable.man_blue;
            }

            status.setContentDescription(away ? context.getString(R.string.user_state_away) + " " + user.szStatusMsg : null);

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

            usericon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            
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
