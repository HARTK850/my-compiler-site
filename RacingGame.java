import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RacingGame extends JFrame {
    
    // === גדלי מסך ===
    public static final int WIDTH = 1024;
    public static final int HEIGHT = 768;
    
    // === מצבי משחק ===
    enum GameState { MAIN_MENU, CAR_SELECT, TRACK_SELECT, UPGRADES, RACING, GAME_OVER }
    private GameState currentState = GameState.MAIN_MENU;
    
    // === נתוני פרופיל ושמירה ===
    private int playerMoney = 5000;
    private int[] carUnlocked = new int[10]; // 1 = unlocked, 0 = locked
    private int currentCarIndex = 0;
    private int currentTrackIndex = 0;
    private String gameMode = "Normal"; // Normal, Time Trial, Championship, Career
    
    // === רמות שדרוג (0 עד 3) ===
    private int engineLevel = 0;
    private int tireLevel = 0;
    private int brakeLevel = 0;
    private int nitroLevel = 0;
    
    // === רשימות הגדרות קבועות ===
    private CarSpec[] cars = new CarSpec[10];
    private TrackSpec[] tracks = new TrackSpec[6];
    
    // === משתני מרוץ פעיל ===
    private double playerPosition = 0; 
    private double playerX = 0; // -1 (שמאל קיצוני) עד 1 (ימין קיצוני)
    private double playerSpeed = 0;
    private double nitroAmount = 100;
    private boolean isNitroActive = false;
    private double trackLength = 0;
    private int currentLap = 1;
    private final int totalLaps = 3;
    private long raceStartTime = 0;
    private long raceTime = 0;
    
    // יריבי AI
    private List<AICar> aiCars = new ArrayList<>();
    
    // אפקטים חזותיים
    private List<Particle> particles = new ArrayList<>();
    private List<TireMark> tireMarks = new ArrayList<>();
    
    // מבנה המסלול הנוכחי בפועל (סגמנטים)
    private List<TrackSegment> segments = new ArrayList<>();
    private int segmentLength = 200; 
    
    // שליטת מקשים
    private boolean keyLeft, keyRight, keyUp, keyDown, keyNitro;
    
    // מצלמה
    private int cameraMode = 0; // 0 = Third Person, 1 = Cockpit
    
    public RacingGame() {
        setTitle("3D Super Racing Java");
        setSize(WIDTH, HEIGHT);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setLocationRelativeTo(null);
        
        initData();
        loadProgress();
        
        GamePanel panel = new GamePanel();
        add(panel);
        
        // מאזיני מקלדת
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKeyPress(e.getKeyCode(), true);
            }
            @Override
            public void keyReleased(KeyEvent e) {
                handleKeyPress(e.getKeyCode(), false);
            }
        });
        
        // לולאת המשחק הראשית (60 FPS בקירוב)
        Timer timer = new Timer(16, e -> {
            updateGame();
            panel.repaint();
        });
        timer.start();
    }
    
    // === אתחול נתונים ===
    private void initData() {
        // 10 מכוניות שונות (שם, מהירות מקסימלית, האצה, בלימה, מחיר)
        cars[0] = new CarSpec("Family Sedan", 150, 1.2, 2.5, 0, Color.BLUE);
        cars[1] = new CarSpec("Hot Hatch", 170, 1.5, 2.8, 1500, Color.RED);
        cars[2] = new CarSpec("Muscle Car", 190, 1.8, 2.0, 3000, Color.ORANGE);
        cars[3] = new CarSpec("JDM Tuner", 210, 2.2, 3.2, 6000, Color.GREEN);
        cars[4] = new CarSpec("Euro Coupe", 230, 2.5, 3.5, 12000, Color.CYAN);
        cars[5] = new CarSpec("Super SUV", 220, 2.8, 4.0, 20000, Color.MAGENTA);
        cars[6] = new CarSpec("Track Toy", 250, 3.2, 4.5, 35000, Color.YELLOW);
        cars[7] = new CarSpec("GT Racer", 280, 3.8, 5.0, 60000, Color.DARK_GRAY);
        cars[8] = new CarSpec("Hypercar", 320, 4.5, 5.5, 120000, Color.WHITE);
        cars[9] = new CarSpec("Prototype F1", 360, 5.5, 7.0, 250000, Color.PINK);
        
        carUnlocked[0] = 1; // המכונית הראשונה פתוחה תמיד
        
        // 6 מסלולים שונים (שם, אורך בסגמנטים, קושי, צבע רקע)
        tracks[0] = new TrackSpec("City Highway", 1500, 1, new Color(135, 206, 235));
        tracks[1] = new TrackSpec("Desert Dunes", 2000, 2, new Color(244, 164, 96));
        tracks[2] = new TrackSpec("Snow Pass", 1800, 3, new Color(220, 220, 243));
        tracks[3] = new TrackSpec("Forest Rally", 2200, 3, new Color(34, 139, 34));
        tracks[4] = new TrackSpec("Industrial Zone", 2500, 4, new Color(70, 80, 90));
        tracks[5] = new TrackSpec("Futuristic Neon", 3000, 5, new Color(10, 10, 25));
    }
    
    // === שמירה וטעינה ===
    private void saveProgress() {
        try (PrintWriter writer = new PrintWriter(new FileWriter("save.dat"))) {
            writer.println(playerMoney);
            for (int u : carUnlocked) writer.print(u + " ");
            writer.println();
            writer.println(engineLevel + " " + tireLevel + " " + brakeLevel + " " + nitroLevel);
        } catch (IOException e) {
            System.out.println("Could not save progress");
        }
    }
    
    private void loadProgress() {
        File file = new File("save.dat");
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            playerMoney = Integer.parseInt(reader.readLine());
            String[] tokens = reader.readLine().split(" ");
            for (int i = 0; i < tokens.length && i < carUnlocked.length; i++) {
                carUnlocked[i] = Integer.parseInt(tokens[i]);
            }
            String[] upTokens = reader.readLine().split(" ");
            engineLevel = Integer.parseInt(upTokens[0]);
            tireLevel = Integer.parseInt(upTokens[1]);
            brakeLevel = Integer.parseInt(upTokens[2]);
            nitroLevel = Integer.parseInt(upTokens[3]);
        } catch (Exception e) {
            System.out.println("Error loading save data, resetting.");
        }
    }
    
    // === בניית מסלול תלת-ממדי פסאודו ===
    private void buildTrack() {
        segments.clear();
        TrackSpec spec = tracks[currentTrackIndex];
        
        Random rand = new Random(currentTrackIndex * 42L);
        double currentCurve = 0;
        double currentHill = 0;
        
        for (int i = 0; i < spec.length; i++) {
            // שינויי עיקולים וגבעות בכל 100 סגמנטים
            if (i % 100 == 0) {
                currentCurve = (rand.nextDouble() * 2 - 1) * spec.difficulty * 2;
                currentHill = (rand.nextDouble() * 2 - 1) * spec.difficulty * 30;
                if (i < 300 || i > spec.length - 200) { // התחלה וסיום ישרים
                    currentCurve = 0;
                    currentHill = 0;
                }
            }
            
            Color grass = ((i / 3) % 2 == 0) ? new Color(16, 120, 16) : new Color(12, 100, 12);
            Color rumble = ((i / 3) % 2 == 0) ? Color.WHITE : Color.RED;
            Color road = Color.DARK_GRAY;
            
            // התאמת צבעים לפי סוג המסלול
            if (currentTrackIndex == 1) { // מדבר
                grass = ((i / 3) % 2 == 0) ? new Color(230, 190, 130) : new Color(220, 180, 120);
                rumble = ((i / 3) % 2 == 0) ? Color.WHITE : new Color(180, 100, 50);
            } else if (currentTrackIndex == 2) { // שלג
                grass = Color.WHITE;
                rumble = ((i / 3) % 2 == 0) ? Color.LIGHT_GRAY : Color.BLUE;
            } else if (currentTrackIndex == 5) { // עתידני
                grass = Color.BLACK;
                road = new Color(20, 20, 40);
                rumble = ((i / 3) % 2 == 0) ? Color.CYAN : Color.MAGENTA;
            }
            
            segments.add(new TrackSegment(i, currentCurve, currentHill, road, grass, rumble));
        }
        
        trackLength = segments.size() * segmentLength;
        
        // יצירת יריבים
        aiCars.clear();
        int totalAI = 5;
        for (int i = 0; i < totalAI; i++) {
            double aiPos = 400 + i * 500;
            double aiX = -0.5 + (i * 0.3);
            double baseSpeed = cars[currentCarIndex].maxSpeed * 0.7 + (rand.nextDouble() * 30);
            aiCars.add(new AICar(aiPos, aiX, baseSpeed, i));
        }
    }
    
    // === ניהול מקשים ===
    private void handleKeyPress(int keyCode, boolean isPressed) {
        if (currentState == GameState.RACING) {
            if (keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_A) keyLeft = isPressed;
            if (keyCode == KeyEvent.VK_RIGHT || keyCode == KeyEvent.VK_D) keyRight = isPressed;
            if (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_W) keyUp = isPressed;
            if (keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_S) keyDown = isPressed;
            if (keyCode == KeyEvent.VK_SPACE) keyNitro = isPressed;
            
            if (isPressed && keyCode == KeyEvent.VK_C) {
                cameraMode = (cameraMode + 1) % 2; // החלפת מצלמה
            }
        }
    }
    
    // === עדכון לוגיקת המשחק ===
    private void updateGame() {
        if (currentState != GameState.RACING) return;
        
        CarSpec spec = cars[currentCarIndex];
        
        // השפעת שדרוגים על הנתונים
        double effectiveMaxSpeed = spec.maxSpeed + (engineLevel * 15);
        double effectiveAccel = spec.acceleration + (engineLevel * 0.3);
        double effectiveBrake = spec.braking + (brakeLevel * 0.5);
        double effectiveHandling = 0.05 + (tireLevel * 0.01);
        
        // טיפול בניטרו
        if (keyNitro && nitroAmount > 0 && keyUp && playerSpeed > 50) {
            isNitroActive = true;
            effectiveMaxSpeed += 50 + (nitroLevel * 10);
            effectiveAccel *= 2;
            nitroAmount -= 0.6;
        } else {
            isNitroActive = false;
            if (nitroAmount < 100) nitroAmount += 0.1; // מילוי איטי
        }
        
        // האצה ובלימה
        if (keyUp) {
            if (playerSpeed < effectiveMaxSpeed) playerSpeed += effectiveAccel;
        } else if (keyDown) {
            if (playerSpeed > 0) playerSpeed -= effectiveBrake;
        } else {
            if (playerSpeed > 0) playerSpeed -= 0.5; // חיכוך טבעי
        }
        
        if (playerSpeed < 0) playerSpeed = 0;
        
        // פנייה והשפעת מהירות
        if (playerSpeed > 0) {
            double handlingFactor = playerSpeed / effectiveMaxSpeed;
            if (keyLeft) {
                playerX -= effectiveHandling * handlingFactor;
                createTireMark();
            }
            if (keyRight) {
                playerX += effectiveHandling * handlingFactor;
                createTireMark();
            }
        }
        
        // השפעת פניית הכביש על הרכב (כוח צנטריפוגלי)
        int currentSegIndex = (int)(playerPosition / segmentLength) % segments.size();
        TrackSegment currentSeg = segments.get(currentSegIndex);
        playerX -= (playerSpeed / effectiveMaxSpeed) * currentSeg.curve * 0.015;
        
        // עדכון מיקום
        playerPosition += playerSpeed * 0.15;
        
        // הגבלה לשוליים וירידה מהכביש
        if (playerX < -2 || playerX > 2) {
            playerX = Math.max(-2, Math.min(2, playerX));
        }
        if (Math.abs(playerX) > 1.0) { // נסיעה על הדשא/חול מאטה את הרכב
            if (playerSpeed > 60) playerSpeed -= 2.0;
            createDustParticle();
        }
        
        // עדכון יריבי AI
        for (AICar ai : aiCars) {
            ai.position += ai.speed * 0.15;
            int aiSegIndex = (int)(ai.position / segmentLength) % segments.size();
            TrackSegment aiSeg = segments.get(aiSegIndex);
            ai.x += aiSeg.curve * 0.005; // ה-AI עוקב חלקית אחרי הסיבוב
            
            // מניעת יציאה מהכביש של ה-AI
            if (ai.x < -0.8) ai.x = -0.8;
            if (ai.x > 0.8) ai.x = 0.8;
            
            // בדיקת התנגשות של השחקן ב-AI
            if (Math.abs(playerPosition - ai.position) < 40 && Math.abs(playerX - ai.x) < 0.3) {
                if (playerSpeed > ai.speed) {
                    playerSpeed = ai.speed * 0.8; // בלימה עקב פגיעה מאחור
                } else {
                    playerSpeed *= 0.7;
                }
            }
        }
        
        // עדכון חלקיקים ואפקטים
        updateEffects();
        
        // ניהול הקפות וזמן
        raceTime = System.currentTimeMillis() - raceStartTime;
        if (playerPosition >= trackLength) {
            if (currentLap < totalLaps) {
                currentLap++;
                playerPosition = 0;
                // איפוס מיקומי ה-AI יחסית
                for (AICar ai : aiCars) ai.position = ai.position % trackLength;
            } else {
                // סיום המרוץ
                endRace();
            }
        }
    }
    
    private void createDustParticle() {
        particles.add(new Particle((int)(WIDTH / 2 + (playerX * WIDTH / 4)), HEIGHT - 120, Color.LIGHT_GRAY));
    }
    
    private void createTireMark() {
        if (playerSpeed > 100) {
            tireMarks.add(new TireMark(playerX, playerPosition));
            if (tireMarks.size() > 200) tireMarks.remove(0);
        }
    }
    
    private void updateEffects() {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.y -= p.speedY;
            p.x += p.speedX;
            p.life--;
            if (p.life <= 0) particles.remove(i);
        }
    }
    
    private void endRace() {
        currentState = GameState.GAME_OVER;
        int reward = 0;
        
        // קביעת מיקום לפי כמה AI עקפנו
        int position = 1;
        for (AICar ai : aiCars) {
            if (ai.position > playerPosition + (totalLaps - 1) * trackLength) {
                position++;
            }
        }
        
        if (position == 1) reward = 2000;
        else if (position == 2) reward = 1200;
        else if (position == 3) reward = 700;
        else reward = 200;
        
        if (gameMode.equals("Career")) {
            reward *= 1.5;
        }
        
        playerMoney += reward;
        saveProgress();
    }
    
    // === מחלקות פנימיות לייצוג נתונים ===
    static class CarSpec {
        String name;
        double maxSpeed;
        double acceleration;
        double braking;
        int price;
        Color color;
        
        CarSpec(String name, double maxSpeed, double acceleration, double braking, int price, Color color) {
            this.name = name;
            this.maxSpeed = maxSpeed;
            this.acceleration = acceleration;
            this.braking = braking;
            this.price = price;
            this.color = color;
        }
    }
    
    static class TrackSpec {
        String name;
        int length;
        int difficulty;
        Color skyColor;
        
        TrackSpec(String name, int length, int difficulty, Color skyColor) {
            this.name = name;
            this.length = length;
            this.difficulty = difficulty;
            this.skyColor = skyColor;
        }
    }
    
    static class TrackSegment {
        int index;
        double curve;
        double hill;
        Color road;
        Color grass;
        Color rumble;
        // נקודות תלת-ממד מחושבות לרנדור
        double screenX, screenY, screenW;
        double worldX, worldY, worldZ;
        
        TrackSegment(int index, double curve, double hill, Color road, Color grass, Color rumble) {
            this.index = index;
            this.curve = curve;
            this.hill = hill;
            this.road = road;
            this.grass = grass;
            this.rumble = rumble;
            this.worldX = 0;
            this.worldY = hill;
            this.worldZ = index * 200; // אורך סגמנט
        }
        
        void project(double camX, double camY, double camZ, double camDepth) {
            double transX = worldX - camX;
            double transY = worldY - camY;
            double transZ = worldZ - camZ;
            
            if (transZ <= 0) transZ = 0.001; // מניעת חלוקה באפס
            
            double scale = camDepth / transZ;
            screenX = (RacingGame.WIDTH / 2) + (scale * transX * RacingGame.WIDTH / 2);
            screenY = (RacingGame.HEIGHT / 2) - (scale * transY * RacingGame.HEIGHT / 2);
            screenW = scale * 1000 * RacingGame.WIDTH / 2; // 1000 רוחב כביש וירטואלי
        }
    }
    
    static class AICar {
        double position;
        double x;
        double speed;
        int id;
        
        AICar(double position, double x, double speed, int id) {
            this.position = position;
            this.x = x;
            this.speed = speed;
            this.id = id;
        }
    }
    
    static class Particle {
        int x, y, life;
        int speedX, speedY;
        Color color;
        
        Particle(int x, int y, Color color) {
            this.x = x;
            this.y = y;
            this.color = color;
            this.life = 10 + new Random().nextInt(10);
            this.speedX = new Random().nextInt(5) - 2;
            this.speedY = 2 + new Random().nextInt(4);
        }
    }
    
    static class TireMark {
        double trackX;
        double trackPos;
        TireMark(double x, double pos) {
            this.trackX = x;
            this.trackPos = pos;
        }
    }
    
    // === פאנל התצוגה והרנדור המרכזי ===
    private class GamePanel extends JPanel {
        
        public GamePanel() {
            setPreferredSize(new Dimension(WIDTH, HEIGHT));
            setDoubleBuffered(true);
            
            // תמיכה בעכבר לתפריטים
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    handleMouseClick(e.getX(), e.getY());
                }
            });
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            switch (currentState) {
                case MAIN_MENU: drawMainMenu(g2); break;
                case CAR_SELECT: drawCarSelect(g2); break;
                case TRACK_SELECT: drawTrackSelect(g2); break;
                case UPGRADES: drawUpgradesMenu(g2); break;
                case RACING: drawRacingView(g2); break;
                case GAME_OVER: drawGameOverScreen(g2); break;
            }
        }
        
        // === ציור תפריט ראשי ===
        private void drawMainMenu(Graphics2D g) {
            g.setColor(new Color(20, 25, 40));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            
            g.setColor(Color.CYAN);
            g.setFont(new Font("Arial", Font.BOLD, 54));
            g.drawString("3D JAVA SUPER RACER", 220, 150);
            
            drawButton(g, "1. START RACE", 362, 260, 300, 50, Color.DARK_GRAY);
            drawButton(g, "2. CAR SELECTION", 362, 330, 300, 50, Color.DARK_GRAY);
            drawButton(g, "3. TRACK SELECTION", 362, 400, 300, 50, Color.DARK_GRAY);
            drawButton(g, "4. GARAGE / UPGRADES", 362, 470, 300, 50, Color.DARK_GRAY);
            drawButton(g, "EXIT", 362, 540, 300, 50, new Color(120, 30, 30));
            
            g.setColor(Color.YELLOW);
            g.setFont(new Font("Arial", Font.PLAIN, 20));
            g.drawString("Cash: $" + playerMoney, 40, 50);
        }
        
        // === ציור בחירת רכב ===
        private void drawCarSelect(Graphics2D g) {
            g.setColor(new Color(30, 30, 45));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 36));
            g.drawString("SELECT YOUR VEHICLE", 320, 80);
            
            CarSpec car = cars[currentCarIndex];
            g.setColor(car.color);
            g.fillRect(362, 140, 300, 150);
            g.setColor(Color.WHITE);
            g.drawRect(362, 140, 300, 150);
            
            g.setFont(new Font("Arial", Font.BOLD, 24));
            g.drawString(car.name, 362, 320);
            g.setFont(new Font("Arial", Font.PLAIN, 18));
            g.drawString("Max Speed: " + car.maxSpeed + " km/h", 362, 360);
            g.drawString("Acceleration: " + car.acceleration, 362, 390);
            g.drawString("Braking: " + car.braking, 362, 420);
            
            boolean unlocked = carUnlocked[currentCarIndex] == 1;
            if (unlocked) {
                g.setColor(Color.GREEN);
                g.drawString("UNLOCKED / OWNED", 362, 460);
                drawButton(g, "SELECT & RETURN", 362, 500, 300, 50, Color.GRAY);
            } else {
                g.setColor(Color.RED);
                g.drawString("LOCKED - Price: $" + car.price, 362, 460);
                drawButton(g, "BUY CAR", 362, 500, 300, 50, new Color(40, 100, 40));
            }
            
            drawButton(g, "< PREV", 200, 200, 120, 50, Color.DARK_GRAY);
            drawButton(g, "NEXT >", 704, 200, 120, 50, Color.DARK_GRAY);
            drawButton(g, "BACK", 40, 680, 120, 40, Color.DARK_GRAY);
        }
        
        // === ציור בחירת מסלול ===
        private void drawTrackSelect(Graphics2D g) {
            g.setColor(new Color(25, 35, 30));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 36));
            g.drawString("SELECT TRACK & MODE", 320, 80);
            
            TrackSpec spec = tracks[currentTrackIndex];
            g.setColor(spec.skyColor);
            g.fillRect(362, 140, 300, 120);
            g.setColor(Color.WHITE);
            g.drawRect(362, 140, 300, 120);
            
            g.setFont(new Font("Arial", Font.BOLD, 24));
            g.drawString("Track: " + spec.name, 362, 290);
            g.drawString("Difficulty: " + "★".repeat(spec.difficulty), 362, 330);
            
            // בחירת מצב משחק
            g.drawString("Game Mode: " + gameMode, 362, 390);
            drawButton(g, "Normal", 250, 430, 120, 40, gameMode.equals("Normal") ? Color.BLUE : Color.DARK_GRAY);
            drawButton(g, "Time Trial", 380, 430, 120, 40, gameMode.equals("Time Trial") ? Color.BLUE : Color.DARK_GRAY);
            drawButton(g, "Championship", 510, 430, 140, 40, gameMode.equals("Championship") ? Color.BLUE : Color.DARK_GRAY);
            drawButton(g, "Career", 660, 430, 120, 40, gameMode.equals("Career") ? Color.BLUE : Color.DARK_GRAY);
            
            drawButton(g, "< PREV TRACK", 180, 170, 160, 50, Color.DARK_GRAY);
            drawButton(g, "NEXT TRACK >", 680, 170, 160, 50, Color.DARK_GRAY);
            
            drawButton(g, "CONFIRM & START", 362, 530, 300, 60, Color.GREEN);
            drawButton(g, "BACK", 40, 680, 120, 40, Color.DARK_GRAY);
        }
        
        // === ציור תפריט שדרוגים ===
        private void drawUpgradesMenu(Graphics2D g) {
            g.setColor(new Color(40, 40, 50));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 36));
            g.drawString("PERFORMANCE GARAGE", 300, 80);
            g.setFont(new Font("Arial", Font.PLAIN, 20));
            g.drawString("Your Funds: $" + playerMoney, 420, 120);
            
            int upgradeCost = 1000; 
            
            drawUpgradeRow(g, "ENGINE (Speed & Accel)", engineLevel, 200, upgradeCost);
            drawUpgradeRow(g, "TIRES (Handling)", tireLevel, 300, upgradeCost);
            drawUpgradeRow(g, "BRAKES (Deceleration)", brakeLevel, 400, upgradeCost);
            drawUpgradeRow(g, "NITRO BOOST", nitroLevel, 500, upgradeCost);
            
            drawButton(g, "BACK TO MENU", 362, 620, 300, 50, Color.DARK_GRAY);
        }
        
        private void drawUpgradeRow(Graphics2D g, String name, int level, int y, int cost) {
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 18));
            g.drawString(name + " [LVL " + level + "/3]", 150, y + 25);
            
            // בר התקדמות רמה
            for (int i = 0; i < 3; i++) {
                g.setColor(i < level ? Color.GREEN : Color.GRAY);
                g.fillRect(450 + (i * 35), y + 10, 30, 20);
            }
            
            if (level < 3) {
                drawButton(g, "UPGRADE ($" + cost + ")", 600, y, 200, 35, Color.DARK_GRAY);
            } else {
                g.setColor(Color.GOLD);
                g.drawString("MAXED OUT", 600, y + 25);
            }
        }
        
        // === מנוע רנדור תלת-ממדי פסאודו למרוץ פועל ===
        private void drawRacingView(Graphics2D g) {
            TrackSpec spec = tracks[currentTrackIndex];
            
            // 1. רקע שמיים
            g.setColor(spec.skyColor);
            g.fillRect(0, 0, WIDTH, HEIGHT / 2);
            
            // אופק וגבעות רחוקות
            g.setColor(spec.skyColor.darker());
            g.fillOval(-100, HEIGHT / 3, WIDTH + 200, HEIGHT / 2);
            
            // 2. חישוב נקודת מצלמה
            int startSegIndex = (int)(playerPosition / segmentLength);
            double camY = 1500 + segments.get(startSegIndex % segments.size()).worldY;
            if (cameraMode == 1) camY = 800; // מצלמת קוקפיט נמוכה יותר
            
            double maxProjectedY = HEIGHT;
            double currentCurveSum = 0;
            
            // רנדור סגמנטים מאחור לפנים (חלון מבט קדימה של 50 סגמנטים)
            for (int i = 0; i < 50; i++) {
                int index = (startSegIndex + i) % segments.size();
                TrackSegment seg = segments.get(index);
                
                // טיפול במעבר קצה מסלול בלולאה
                double camZOffset = (startSegIndex + i >= segments.size()) ? trackLength : 0;
                
                seg.project(playerX * 1000 - currentCurveSum, camY, playerPosition - camZOffset, 0.8);
                currentCurveSum += seg.curve * 3;
                
                if (seg.screenY >= maxProjectedY) continue; // מניעת ציור סגמנטים מוסתרים
                maxProjectedY = seg.screenY;
                
                if (i == 0) continue;
                
                TrackSegment prev = segments.get((index - 1 + segments.size()) % segments.size());
                
                // ציור שדות (Grass)
                g.setColor(seg.grass);
                g.fillRect(0, (int)seg.screenY, WIDTH, (int)(prev.screenY - seg.screenY + 2));
                
                // ציור כביש ושוליים (Road & Rumble)
                int[] xPoints = {
                    (int)(prev.screenX - prev.screenW), (int)(seg.screenX - seg.screenW),
                    (int)(seg.screenX + seg.screenW), (int)(prev.screenX + prev.screenW)
                };
                int[] yPoints = { (int)prev.screenY, (int)seg.screenY, (int)seg.screenY, (int)prev.screenY };
                
                // שוליים חיצוניים (Rumble stripes)
                g.setColor(seg.rumble);
                g.fillPolygon(xPoints, yPoints, 4);
                
                // כביש ראשי דחוס פנימה במעט
                int[] xRoadPoints = {
                    (int)(prev.screenX - prev.screenW * 0.9), (int)(seg.screenX - seg.screenW * 0.9),
                    (int)(seg.screenX + seg.screenW * 0.9), (int)(prev.screenX + prev.screenW * 0.9)
                };
                g.setColor(seg.road);
                g.fillPolygon(xRoadPoints, yPoints, 4);
            }
            
            // 3. רנדור סימני צמיגים
            g.setColor(new Color(0, 0, 0, 80));
            for (TireMark tm : tireMarks) {
                if (tm.trackPos > playerPosition && tm.trackPos < playerPosition + 5000) {
                    double relativeZ = tm.trackPos - playerPosition;
                    double scale = 0.8 / relativeZ;
                    int screenX = (int)((WIDTH / 2) + (scale * (tm.trackX - playerX) * 1000 * WIDTH / 2));
                    int screenY = (int)((HEIGHT / 2) - (scale * (camY - 200) * HEIGHT / 2));
                    int w = (int)(scale * 40 * WIDTH / 2);
                    if (screenY < HEIGHT && screenY > HEIGHT / 2) {
                        g.fillRect(screenX, screenY, w, 4);
                    }
                }
            }
            
            // 4. רנדור מכוניות AI בתלת-ממד יחסי
            for (AICar ai : aiCars) {
                if (ai.position > playerPosition && ai.position < playerPosition + 8000) {
                    double relativeZ = ai.position - playerPosition;
                    double scale = 0.8 / relativeZ;
                    
                    // חישוב עקמומיות מקורבת למיקום ה-AI
                    int aiSeg = (int)(ai.position / segmentLength) % segments.size();
                    
                    int screenX = (int)((WIDTH / 2) + (scale * (ai.x - playerX) * 1000 * WIDTH / 2));
                    int screenY = (int)((HEIGHT / 2) - (scale * (segments.get(aiSeg).worldY - camY) * HEIGHT / 2));
                    int size = (int)(scale * 1200 * WIDTH / 2);
                    
                    if (size > 5 && size < WIDTH) {
                        g.setColor(Color.RED); // צבע אויב בסיסי
                        g.fillRect(screenX - size / 2, screenY - size, size, (int)(size * 0.6));
                        g.setColor(Color.BLACK);
                        g.fillRect(screenX - size / 2, screenY - (int)(size * 0.3), (int)(size * 0.2), (int)(size * 0.3));
                        g.fillRect(screenX + size / 2 - (int)(size * 0.2), screenY - (int)(size * 0.3), (int)(size * 0.2), (int)(size * 0.3));
                    }
                }
            }
            
            // 5. רנדור אפקטי חלקיקים
            for (Particle p : particles) {
                g.setColor(p.color);
                g.fillOval(p.x, p.y, p.life / 2, p.life / 2);
            }
            
            // 6. ציור מכונית השחקן (אם גוף שלישי)
            if (cameraMode == 0) {
                g.setColor(cars[currentCarIndex].color);
                int carW = 160;
                int carH = 90;
                int carX = WIDTH / 2 - carW / 2;
                int carY = HEIGHT - 160;
                
                // גוף המכונית
                g.fillRect(carX, carY, carW, carH);
                // גג וחלונות
                g.setColor(Color.DARK_GRAY);
                g.fillRect(carX + 25, carY - 30, carW - 50, 30);
                // גלגלים
                g.setColor(Color.BLACK);
                g.fillRect(carX - 10, carY + 40, 20, 40);
                g.fillRect(carX + carW - 10, carY + 40, 20, 40);
                
                // אורות בלימה/ניטרו
                if (keyDown) {
                    g.setColor(Color.RED);
                    g.fillRect(carX + 10, carY + 10, 20, 10);
                    g.fillRect(carX + carW - 30, carY + 10, 20, 10);
                }
                if (isNitroActive) {
                    g.setColor(Color.CYAN);
                    g.fillRect(carX + carW / 2 - 15, carY + carH, 30, 20);
                }
            }
            
            // 7. תצוגת לוח מחוונים (HUD)
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(0, 0, WIDTH, 60);
            g.fillRect(0, HEIGHT - 80, 280, 80);
            
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 18));
            g.drawString("MODE: " + gameMode, 20, 35);
            g.drawString("LAP: " + currentLap + " / " + totalLaps, 220, 35);
            g.drawString("TIME: " + (raceTime / 1000.0) + "s", 400, 35);
            g.drawString("TRACK: " + spec.name, 600, 35);
            
            // מד מהירות וניטרו
            g.setFont(new Font("Arial", Font.BOLD, 26));
            g.drawString((int)playerSpeed + " KM/H", 30, HEIGHT - 45);
            
            g.setFont(new Font("Arial", Font.PLAIN, 12));
            g.drawString("NITRO:", 30, HEIGHT - 15);
            g.setColor(Color.GRAY);
            g.fillRect(80, HEIGHT - 25, 150, 12);
            g.setColor(Color.CYAN);
            g.fillRect(80, HEIGHT - 25, (int)(nitroAmount * 1.5), 12);
        }
        
        // === מסך סיום מרוץ ===
        private void drawGameOverScreen(Graphics2D g) {
            g.setColor(new Color(10, 10, 20));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            
            g.setColor(Color.GOLD);
            g.setFont(new Font("Arial", Font.BOLD, 48));
            g.drawString("RACE FINISHED!", 340, 200);
            
            int position = 1;
            for (AICar ai : aiCars) {
                if (ai.position > playerPosition + (totalLaps - 1) * trackLength) position++;
            }
            
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 28));
            g.drawString("Your Position: Final #" + position, 380, 290);
            g.drawString("Total Time: " + (raceTime / 1000.0) + " seconds", 380, 340);
            
            int reward = position == 1 ? 2000 : (position == 2 ? 1200 : 500);
            g.setColor(Color.GREEN);
            g.drawString("Prize Money Earned: $" + reward, 380, 400);
            
            drawButton(g, "RETURN TO MENU", 362, 500, 300, 50, Color.DARK_GRAY);
        }
        
        // כלי עזר לציור כפתורים
        private void drawButton(Graphics2D g, String text, int x, int y, int w, int h, Color bg) {
            g.setColor(bg);
            g.fillRect(x, y, w, h);
            g.setColor(Color.WHITE);
            g.drawRect(x, y, w, h);
            g.setFont(new Font("Arial", Font.BOLD, 16));
            FontMetrics fm = g.getFontMetrics();
            int tx = x + (w - fm.stringWidth(text)) / 2;
            int ty = y + (h - fm.getHeight()) / 2 + fm.getAscent();
            g.drawString(text, tx, ty);
        }
    }
    
    // === ניהול לחיצות עכבר ותפריטים ===
    private void handleMouseClick(int mx, int my) {
        if (currentState == GameState.MAIN_MENU) {
            if (checkBounds(mx, my, 362, 260, 300, 50)) {
                buildTrack();
                raceStartTime = System.currentTimeMillis();
                currentLap = 1;
                playerPosition = 0;
                playerX = 0;
                playerSpeed = 0;
                currentState = GameState.RACING;
            }
            else if (checkBounds(mx, my, 362, 330, 300, 50)) currentState = GameState.CAR_SELECT;
            else if (checkBounds(mx, my, 362, 400, 300, 50)) currentState = GameState.TRACK_SELECT;
            else if (checkBounds(mx, my, 362, 470, 300, 50)) currentState = GameState.UPGRADES;
            else if (checkBounds(mx, my, 362, 540, 300, 50)) System.exit(0);
        } 
        else if (currentState == GameState.CAR_SELECT) {
            if (checkBounds(mx, my, 200, 200, 120, 50)) {
                currentCarIndex = (currentCarIndex - 1 + 10) % 10;
            }
            else if (checkBounds(mx, my, 704, 200, 120, 50)) {
                currentCarIndex = (currentCarIndex + 1) % 10;
            }
            else if (checkBounds(mx, my, 362, 500, 300, 50)) {
                CarSpec car = cars[currentCarIndex];
                if (carUnlocked[currentCarIndex] == 1) {
                    currentState = GameState.MAIN_MENU;
                } else if (playerMoney >= car.price) {
                    playerMoney -= car.price;
                    carUnlocked[currentCarIndex] = 1;
                    saveProgress();
                }
            }
            else if (checkBounds(mx, my, 40, 680, 120, 40)) currentState = GameState.MAIN_MENU;
        }
        else if (currentState == GameState.TRACK_SELECT) {
            if (checkBounds(mx, my, 180, 170, 160, 50)) {
                currentTrackIndex = (currentTrackIndex - 1 + 6) % 6;
            }
            else if (checkBounds(mx, my, 680, 170, 160, 50)) {
                currentTrackIndex = (currentTrackIndex + 1) % 6;
            }
            else if (checkBounds(mx, my, 250, 430, 120, 40)) gameMode = "Normal";
            else if (checkBounds(mx, my, 380, 430, 120, 40)) gameMode = "Time Trial";
            else if (checkBounds(mx, my, 510, 430, 140, 40)) gameMode = "Championship";
            else if (checkBounds(mx, my, 660, 430, 120, 40)) gameMode = "Career";
            else if (checkBounds(mx, my, 362, 530, 300, 60)) {
                buildTrack();
                raceStartTime = System.currentTimeMillis();
                currentLap = 1;
                playerPosition = 0;
                playerX = 0;
                playerSpeed = 0;
                currentState = GameState.RACING;
            }
            else if (checkBounds(mx, my, 40, 680, 120, 40)) currentState = GameState.MAIN_MENU;
        }
        else if (currentState == GameState.UPGRADES) {
            int cost = 1000;
            if (checkBounds(mx, my, 600, 200, 200, 35) && engineLevel < 3 && playerMoney >= cost) {
                engineLevel++; playerMoney -= cost;
            }
            else if (checkBounds(mx, my, 600, 300, 200, 35) && tireLevel < 3 && playerMoney >= cost) {
                tireLevel++; playerMoney -= cost;
            }
            else if (checkBounds(mx, my, 600, 400, 200, 35) && brakeLevel < 3 && playerMoney >= cost) {
                brakeLevel++; playerMoney -= cost;
            }
            else if (checkBounds(mx, my, 600, 500, 200, 35) && nitroLevel < 3 && playerMoney >= cost) {
                nitroLevel++; playerMoney -= cost;
            }
            else if (checkBounds(mx, my, 362, 620, 300, 50)) {
                saveProgress();
                currentState = GameState.MAIN_MENU;
            }
        }
        else if (currentState == GameState.GAME_OVER) {
            if (checkBounds(mx, my, 362, 500, 300, 50)) currentState = GameState.MAIN_MENU;
        }
    }
    
    private boolean checkBounds(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
    
    // === נקודת הכניסה הראשית של התוכנית ===
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            RacingGame game = new RacingGame();
            game.setVisible(true);
        });
    }
}