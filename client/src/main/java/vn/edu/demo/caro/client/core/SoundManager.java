package vn.edu.demo.caro.client.core;

import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class SoundManager {
    private static SoundManager instance;
    private final Map<String, AudioClip> soundEffects = new HashMap<>();
    private MediaPlayer bgmPlayer;
    private boolean isMuted = false;

    // Đường dẫn khớp với thư mục trong cây dự án của bạn
    private static final String BASE_PATH = "/vn/edu/demo/caro/client/Music/";

    private SoundManager() {
        // Ánh xạ tên chức năng -> tên file thực tế của bạn
        loadSound("move", "dat_co.mp3");       // Tiếng đặt cờ
        loadSound("notify", "tin_nhan.mp3");   // Tiếng tin nhắn/thông báo
        loadSound("win", "thang_cuoc.mp3");    // Tiếng thắng
        loadSound("lose", "thua_cuoc.mp3");    // Tiếng thua
    }
public boolean isMuted() {
        return isMuted;
    }
    public static SoundManager getInstance() {
        if (instance == null) {
            instance = new SoundManager();
        }
        return instance;
    }

    private void loadSound(String name, String fileName) {
        try {
            String fullPath = BASE_PATH + fileName;
            URL url = getClass().getResource(fullPath);
            if (url != null) {
                // AudioClip phù hợp cho hiệu ứng ngắn, độ trễ thấp
                soundEffects.put(name, new AudioClip(url.toExternalForm()));
            } else {
                System.err.println("[SoundManager] Không tìm thấy file nhạc: " + fullPath);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- CÁC HÀM PHÁT HIỆU ỨNG ---

    public void playMove() {
        playSound("move");
    }

    public void playNotify() {
        playSound("notify");
    }

    public void playWin() {
        stopBgm(); // Dừng nhạc nền để nghe rõ tiếng thắng
        playSound("win");
    }

    public void playLose() {
        stopBgm();
        playSound("lose");
    }

    private void playSound(String name) {
        if (isMuted) return;
        AudioClip clip = soundEffects.get(name);
        if (clip != null) {
            if (clip.isPlaying()) clip.stop(); // Dừng âm thanh cũ nếu đang chạy để tránh chồng âm
            clip.play();
        }
    }

    // --- NHẠC NỀN (BGM) ---

    public void playBgm() {
        if (isMuted || bgmPlayer != null) return;
        try {
            String path = BASE_PATH + "nhac_nen.mp3";
            URL url = getClass().getResource(path);
            if (url != null) {
                Media media = new Media(url.toExternalForm());
                bgmPlayer = new MediaPlayer(media);
                bgmPlayer.setCycleCount(MediaPlayer.INDEFINITE); // Lặp vô tận
                bgmPlayer.setVolume(0.5); // Âm lượng 50%
                bgmPlayer.play();
            } else {
                System.err.println("[SoundManager] Không tìm thấy nhạc nền: " + path);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopBgm() {
        if (bgmPlayer != null) {
            bgmPlayer.stop();
            bgmPlayer.dispose();
            bgmPlayer = null;
        }
    }
    
    public void toggleMute() {
        isMuted = !isMuted;
        if (isMuted) stopBgm();
        else playBgm();
    }
}