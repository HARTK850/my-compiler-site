import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;
import java.util.List;

public class Main extends ApplicationAdapter {

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Ultra Racing 3D - LibGDX Edition");
        config.setWindowedMode(1024, 768);
        config.setResizable(false);
        new Lwjgl3Application(new Main(), config);
    }

    // === מצבי משחק ===
    enum GameState { MAIN_MENU, CAR_SELECT, TRACK_SELECT, UPGRADES, RACING, GAME_OVER }
    private GameState currentState = GameState.MAIN_MENU;

    // === רכיבי גרפיקה ורנדור של LibGDX ===
    private SpriteBatch spriteBatch;
    private BitmapFont font;
    private ModelBatch modelBatch;
    private PerspectiveCamera cam3D;
    private OrthographicCamera cam2D;
    private Environment environment;

    // === מודלים תלת-ממדיים ומבנים ===
    private Model carModel;
    private Model roadSegmentModel;
    private Model wallModel;
    private Model enemyModel;
    
    private ModelInstance playerCarInstance;
    private List<ModelInstance> roadInstances = new ArrayList<>();
    private List<EnemyVehicle> enemies = new ArrayList<>();

    // === נתוני פרופיל, כסף ושדרוגים ===
    private int playerMoney = 5000;
    private int currentCarIndex = 0;
    private int currentTrackIndex = 0;
    private int engineLevel = 0;
    private int tireLevel = 0;
    private int nitroLevel = 0;

    // === משתני פיזיקה ומסלול מרוץ ===
    private float playerProgress = 0;
    private float playerX = 0; // היסט ממרכז הכביש (-10 עד 10)
    private float playerSpeed = 0;
    private float maxSpeed = 150f;
    private float acceleration = 25f;
    private float handling = 4f;
    private float trackLength = 5000f;
    private float nitroAmount = 100f;
    private boolean isNitroActive = false;

    // === נתוני הגדרות רכבים ומסלולים ===
    private CarConfig[] cars = new CarConfig[3];
    private TrackConfig[] tracks = new TrackConfig[3];

    static class CarConfig {
        String name; Color color; float baseMaxSpeed; float baseAccel;
        CarConfig(String n, Color c, float s, float a) { name = n; color = c; baseMaxSpeed = s; baseAccel = a; }
    }

    static class TrackConfig {
        String name; Color roadColor; float length; int difficulty;
        TrackConfig(String n, Color c, float l, int d) { name = n; roadColor = c; length = l; difficulty = d; }
    }

    static class EnemyVehicle {
        ModelInstance instance; float progress; float trackX; float speed;
        EnemyVehicle(ModelInstance inst, float p, float x, float s) { instance = inst; progress = p; trackX = x; speed = s; }
    }

    @Override
    public void create() {
        spriteBatch = new SpriteBatch();
        font = new BitmapFont();
        font.getData().setScale(1.8f);
        modelBatch = new ModelBatch();

        // 1. הגדרת מצלמה תלת ממדית אמיתית (Perspective) למרוץ
        cam3D = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam3D.near = 1f;
        cam3D.far = 1000f;

        // 2. הגדרת מצלמה דו ממדית שטוחה לתפריטים וממשק (UI)
        cam2D = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam2D.setToOrtho(false, 1024, 768);

        // 3. הגדרת מערכת תאורה וסביבה תלת ממדית
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.5f, 0.5f, 0.5f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -1f, -0.5f));

        // 4. אתחול נתונים מובנים
        cars[0] = new CarConfig("Swift Cruiser", Color.RED, 140f, 25f);
        cars[1] = new CarConfig("Interceptor Proto", Color.BLUE, 165f, 30f);
        cars[2] = new CarConfig("Apex Titan", Color.YELLOW, 190f, 35f);

        tracks[0] = new TrackConfig("Neon Highway", Color.DARK_GRAY, 4000f, 1);
        tracks[1] = new TrackConfig("Desert Canyon", Color.TAN, 6000f, 2);
        tracks[2] = new TrackConfig("Midnight Grid", Color.BLACK, 8000f, 3);

        // 5. בניית מודלים גאומטריים תלת ממדיים מאפס באמצעות התוכנה
        build3DModels();
        setupRaceEnvironment();
    }

    private void build3DModels() {
        ModelBuilder modelBuilder = new ModelBuilder();
        
        // יצירת מודל השחקן (מכונית תלת ממדית מלבנית מורכבת)
        carModel = modelBuilder.createBox(3f, 1.5f, 5f, 
            new Material(ColorAttribute.createDiffuse(cars[currentCarIndex].color)),
            Usage.Position | Usage.Normal);

        // יצירת מודל האויבים
        enemyModel = modelBuilder.createBox(3f, 1.5f, 5f, 
            new Material(ColorAttribute.createDiffuse(Color.ORANGE)),
            Usage.Position | Usage.Normal);

        // יצירת מקטע כביש תלת ממדי
        roadSegmentModel = modelBuilder.createBox(24f, 0.1f, 40f, 
            new Material(ColorAttribute.createDiffuse(Color.LIGHT_GRAY)),
            Usage.Position | Usage.Normal);

        // יצירת קירות בצידי הדרך
        wallModel = modelBuilder.createBox(0.5f, 2f, 40f, 
            new Material(ColorAttribute.createDiffuse(Color.RED)),
            Usage.Position | Usage.Normal);
    }

    private void setupRaceEnvironment() {
        roadInstances.clear();
        enemies.clear();
        trackLength = tracks[currentTrackIndex].length;

        // בנייה פיזית של המסלול במרחב התלת ממדי לאורך קו ה-Z
        for (int i = 0; i < (trackLength / 40) + 5; i++) {
            ModelInstance roadInst = new ModelInstance(roadSegmentModel);
            // מיקום כל מקטע כביש אחד אחרי השני לאורך ציר ה-Z השלילי
            roadInst.transform.setToTranslation(0, 0, -i * 40f);
            roadInstances.add(roadInst);
            
            // הוספת קירות בצידי הדרך
            ModelInstance leftWall = new ModelInstance(wallModel);
            leftWall.transform.setToTranslation(-12f, 1f, -i * 40f);
            roadInstances.add(leftWall);

            ModelInstance rightWall = new ModelInstance(wallModel);
            rightWall.transform.setToTranslation(12f, 1f, -i * 40f);
            roadInstances.add(rightWall);
        }

        playerCarInstance = new ModelInstance(carModel);

        // יצירת רכבי אויב במיקומים שונים לאורך המסלול
        for (int i = 1; i <= 15; i++) {
            ModelInstance enemyInst = new ModelInstance(enemyModel);
            float startZ = -i * 300f;
            float startX = MathUtils.random(-8f, 8f);
            float enemySpeed = MathUtils.random(60f, 110f);
            enemies.add(new EnemyVehicle(enemyInst, -startZ, startX, enemySpeed));
        }
    }

    @Override
    public void render() {
        // ניקוי חוצצי המסך וכרטיס המסך בכל פריים מחדש
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        // ניהול מצבי המשחק (לוגיקה ורנדור)
        switch (currentState) {
            case MAIN_MENU:
                handleMainMenuInput();
                drawMainMenu();
                break;
            case CAR_SELECT:
                handleCarSelectInput();
                drawCarSelect();
                break;
            case TRACK_SELECT:
                handleTrackSelectInput();
                drawTrackSelect();
                break;
            case UPGRADES:
                handleUpgradesInput();
                drawUpgrades();
                break;
            case RACING:
                updateRaceLogic(Gdx.graphics.getDeltaTime());
                drawRace3DScene();
                drawRaceHUD();
                break;
            case GAME_OVER:
                handleGameOverInput();
                drawGameOver();
                break;
        }
    }

    // === לוגיקת משחק ועדכון הפיזיקה ===
    private void updateRaceLogic(float deltaTime) {
        // חישוב מהירות מקסימלית ותאוצה לפי רמות השדרוגים הנוכחיות
        float currentMaxSpeed = cars[currentCarIndex].baseMaxSpeed + (engineLevel * 15f);
        float currentAccel = cars[currentCarIndex].baseAccel + (engineLevel * 5f);
        float currentHandling = handling + (tireLevel * 1.2f);

        // ניהול מערכת ניטרו (Nitro Boost)
        if (Gdx.input.isKeyPressed(Input.Keys.SPACE) && nitroAmount > 0) {
            isNitroActive = true;
            currentMaxSpeed += 40f + (nitroLevel * 10f);
            currentAccel *= 2f;
            nitroAmount -= deltaTime * 25f;
        } else {
            isNitroActive = false;
            if (nitroAmount < 100f) nitroAmount += deltaTime * 5f; // התחדשות איטית
        }

        // קלט תנועה קדימה ואחורה
        if (Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W)) {
            playerSpeed += currentAccel * deltaTime;
            if (playerSpeed > currentMaxSpeed) playerSpeed = currentMaxSpeed;
        } else if (Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S)) {
            playerSpeed -= currentAccel * 1.5f * deltaTime;
            if (playerSpeed < 0) playerSpeed = 0;
        } else {
            // חיכוך והאטה טבעית כשלא לוחצים על הגז
            playerSpeed -= 15f * deltaTime;
            if (playerSpeed < 0) playerSpeed = 0;
        }

        // קלט פנייה ימינה ושמאלה
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A)) {
            playerX -= currentHandling * (playerSpeed / currentMaxSpeed) * deltaTime * 3f;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D)) {
            playerX += currentHandling * (playerSpeed / currentMaxSpeed) * deltaTime * 3f;
        }

        // חסימת שולי הכביש ומניעת יציאה מהמסלול
        if (playerX < -10f) { playerX = -10f; playerSpeed *= 0.98f; }
        if (playerX > 10f) { playerX = 10f; playerSpeed *= 0.98f; }

        // עדכון התקדמות השחקן לאורך ציר ה-Z
        playerProgress += (playerSpeed * 0.277f) * deltaTime; // המרה לקמ"ש פנימי

        // עדכון מיקום הרכב התלת ממדי של השחקן בעולם
        playerCarInstance.transform.setToTranslation(playerX, 0.75f, -playerProgress);

        // הגדרת מיקום מצלמה תלת ממדית דינמית מאחורי הרכב של השחקן
        cam3D.position.set(playerX * 0.6f, 5.5f, -playerProgress + 14f);
        cam3D.lookAt(playerX, 2f, -playerProgress - 30f);
        cam3D.update();

        // עדכון ופיזיקה עבור מכוניות האויבים
        for (EnemyVehicle enemy : enemies) {
            enemy.progress += (enemy.speed * 0.277f) * deltaTime;
            enemy.instance.transform.setToTranslation(enemy.trackX, 0.75f, -enemy.progress);

            // בדיקת התנגשות (Collision Box Detection) בין השחקן לאויב
            float distanceZ = Math.abs(playerProgress - enemy.progress);
            float distanceX = Math.abs(playerX - enemy.trackX);
            if (distanceZ < 4.5f && distanceX < 2.5f) {
                // התנגשות! האטה דרסטית של השחקן ודחיפה
                playerSpeed *= 0.5f;
                enemy.speed += 20f;
                playerX += (playerX > enemy.trackX) ? 1.5f : -1.5f;
            }
        }

        // בדיקת הגעה לקו הסיום והשלמת המרוץ
        if (playerProgress >= trackLength) {
            int reward = 1500 + (currentTrackIndex * 500);
            playerMoney += reward;
            currentState = GameState.GAME_OVER;
        }
    }

    // === רנדור הסצנה התלת ממדית (3D Game Scene) ===
    private void drawRace3DScene() {
        modelBatch.begin(cam3D);
        
        // ציור הכביש והקירות שנמצאים בטווח הראייה
        for (ModelInstance inst : roadInstances) {
            modelBatch.render(inst, environment);
        }

        // ציור רכבי האויבים
        for (EnemyVehicle enemy : enemies) {
            modelBatch.render(enemy.instance, environment);
        }

        // ציור רכב השחקן
        modelBatch.render(playerCarInstance, environment);
        
        modelBatch.end();
    }

    // === רנדור ממשקי המשתמש (2D HUD & Menus) ===
    private void drawRaceHUD() {
        spriteBatch.setProjectionMatrix(cam2D.combined);
        spriteBatch.begin();
        
        font.setColor(Color.WHITE);
        font.draw(spriteBatch, "SPEED: " + String.format("%.0f", playerSpeed) + " KM/H", 40, 720);
        font.draw(spriteBatch, "PROGRESS: " + String.format("%.0f", playerProgress) + "m / " + (int)trackLength + "m", 40, 670);
        
        // מד ניטרו צבעוני
        font.draw(spriteBatch, "NITRO:", 40, 620);
        font.setColor(isNitroActive ? Color.CYAN : Color.ORANGE);
        font.draw(spriteBatch, String.format("%.0f%%", nitroAmount), 140, 620);

        font.setColor(Color.YELLOW);
        font.draw(spriteBatch, "CASH: $" + playerMoney, 800, 720);
        
        spriteBatch.end();
    }

    private void drawMainMenu() {
        spriteBatch.setProjectionMatrix(cam2D.combined);
        spriteBatch.begin();
        font.setColor(Color.RED);
        font.getData().setScale(3f);
        font.draw(spriteBatch, "ULTRA RACING 3D", 320, 550);
        
        font.getData().setScale(1.6f);
        font.setColor(Color.WHITE);
        font.draw(spriteBatch, "1. START GAME", 420, 400);
        font.draw(spriteBatch, "2. GARAGE & UPGRADES", 420, 340);
        font.draw(spriteBatch, "3. EXIT", 420, 280);
        
        font.setColor(Color.GRAY);
        font.draw(spriteBatch, "Use Numbers (1-3) to choose options", 340, 150);
        spriteBatch.end();
    }

    private void drawCarSelect() {
        spriteBatch.setProjectionMatrix(cam2D.combined);
        spriteBatch.begin();
        font.setColor(Color.CYAN);
        font.draw(spriteBatch, "SELECT YOUR VEHICLE", 380, 600);
        
        for (int i = 0; i < cars.length; i++) {
            if (i == currentCarIndex) font.setColor(Color.GREEN);
            else font.setColor(Color.WHITE);
            font.draw(spriteBatch, (i + 1) + ". " + cars[i].name + " [Max: " + cars[i].baseMaxSpeed + " km/h]", 300, 450 - (i * 60));
        }
        
        font.setColor(Color.YELLOW);
        font.draw(spriteBatch, "Press ENTER to confirm Vehicle", 350, 200);
        spriteBatch.end();
    }

    private void drawTrackSelect() {
        spriteBatch.setProjectionMatrix(cam2D.combined);
        spriteBatch.begin();
        font.setColor(Color.MAGENTA);
        font.draw(spriteBatch, "SELECT TRACK", 430, 600);
        
        for (int i = 0; i < tracks.length; i++) {
            if (i == currentTrackIndex) font.setColor(Color.GREEN);
            else font.setColor(Color.WHITE);
            font.draw(spriteBatch, (i + 1) + ". " + tracks[i].name + " (" + (int)tracks[i].length + "m)", 350, 450 - (i * 60));
        }
        
        font.setColor(Color.YELLOW);
        font.draw(spriteBatch, "Press ENTER to Launch Race!", 350, 200);
        spriteBatch.end();
    }

    private void drawUpgrades() {
        spriteBatch.setProjectionMatrix(cam2D.combined);
        spriteBatch.begin();
        font.setColor(Color.YELLOW);
        font.draw(spriteBatch, "GARAGE SHOP - CASH: $" + playerMoney, 350, 650);
        
        font.setColor(Color.WHITE);
        font.draw(spriteBatch, "1. ENGINE LEVEL: " + engineLevel + "/3 ($1000)", 280, 500);
        font.draw(spriteBatch, "2. TIRES LEVEL: " + tireLevel + "/3 ($1000)", 280, 430);
        font.draw(spriteBatch, "3. NITRO LEVEL: " + nitroLevel + "/3 ($1000)", 280, 360);
        font.draw(spriteBatch, "B. BACK TO MAIN MENU", 280, 260);
        
        font.setColor(Color.GRAY);
        font.draw(spriteBatch, "Press keys 1, 2, or 3 to purchase upgrades", 280, 150);
        spriteBatch.end();
    }

    private void drawGameOver() {
        spriteBatch.setProjectionMatrix(cam2D.combined);
        spriteBatch.begin();
        font.setColor(Color.GREEN);
        font.getData().setScale(2.5f);
        font.draw(spriteBatch, "RACE FINISHED!", 360, 500);
        
        font.getData().setScale(1.6f);
        font.setColor(Color.WHITE);
        font.draw(spriteBatch, "You earned prize money for completing the track!", 280, 400);
        font.draw(spriteBatch, "Press ENTER to return to Main Menu", 320, 300);
        spriteBatch.end();
    }

    // === ניהול קלטים ומקשים עבור המסכים ===
    private void handleMainMenuInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) {
            currentState = GameState.CAR_SELECT;
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) {
            currentState = GameState.UPGRADES;
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) {
            Gdx.app.exit();
        }
    }

    private void handleCarSelectInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) currentCarIndex = 0;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) currentCarIndex = 1;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) currentCarIndex = 2;
        
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            // עדכון צבע מודל השחקן בהתאם לבחירה
            build3DModels(); 
            currentState = GameState.TRACK_SELECT;
        }
    }

    private void handleTrackSelectInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) currentTrackIndex = 0;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) currentTrackIndex = 1;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) currentTrackIndex = 2;
        
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            // איפוס משתני מרוץ וטעינת הסביבה הגרפית מחדש
            playerProgress = 0;
            playerX = 0;
            playerSpeed = 0;
            nitroAmount = 100f;
            setupRaceEnvironment();
            currentState = GameState.RACING;
        }
    }

    private void handleUpgradesInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1) && engineLevel < 3 && playerMoney >= 1000) {
            engineLevel++; playerMoney -= 1000;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2) && tireLevel < 3 && playerMoney >= 1000) {
            tireLevel++; playerMoney -= 1000;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3) && nitroLevel < 3 && playerMoney >= 1000) {
            nitroLevel++; playerMoney -= 1000;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.B)) {
            currentState = GameState.MAIN_MENU;
        }
    }

    private void handleGameOverInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            font.getData().setScale(1.8f);
            currentState = GameState.MAIN_MENU;
        }
    }

    @Override
    public void dispose() {
        // שחרור מסודר של כל רכיבי הגרפיקה והזיכרון מכרטיס המסך
        spriteBatch.dispose();
        font.dispose();
        modelBatch.dispose();
        carModel.dispose();
        enemyModel.dispose();
        roadSegmentModel.dispose();
        wallModel.dispose();
    }
}
