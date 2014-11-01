package com.joel.Platformer.states;

import static com.joel.Platformer.handlers.B2DVars.PPM;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL10;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer.Cell;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.physics.box2d.ChainShape;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Filter;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import com.joel.Platformer.entities.Crystal;
import com.joel.Platformer.entities.HUD;
import com.joel.Platformer.entities.Player;
import com.joel.Platformer.handlers.B2DVars;
import com.joel.Platformer.handlers.GameStateManager;
import com.joel.Platformer.handlers.MyContactListener;
import com.joel.Platformer.handlers.MyInput;
import com.joel.Platformer.main.Game;

public class Play extends GameState {

	private boolean debug = false;

	private World world;
	private Box2DDebugRenderer b2dr;

	private OrthographicCamera b2dCam;

	private MyContactListener cl;

	private TiledMap tileMap;
	private float tileSize;
	private OrthogonalTiledMapRenderer tmr;

	private Player player;
	private Array<Crystal> crystals;
	
	private HUD hud;

	public Play(GameStateManager gsm) {

		super(gsm);

		// set up box2d stuff
		world = new World(new Vector2(0, -9.81f), true);
		cl = new MyContactListener();
		world.setContactListener(cl);
		b2dr = new Box2DDebugRenderer();

		// create player
		createPlayer();

		// create tiles
		createTiles();

		// create Crystals
		createCrystals();

		// create hud
		hud = new HUD(player);
		
		// set up box2d cam
		b2dCam = new OrthographicCamera();
		b2dCam.setToOrtho(false, Game.V_WIDTH / PPM, Game.V_HEIGHT / PPM);

	}

	public void handleInput() {

		// player jump
		if (MyInput.isPressed(MyInput.BUTTON1)) {
			if (cl.isPlayerOnGround()) {
				player.getBody().applyForceToCenter(0, 250, true);
			}
		}
		
		// Switch blocks
		if(MyInput.isPressed(MyInput.BUTTON2)) {
			switchBlocks();
		}

	}

	public void update(float dt) {

		// check input
		handleInput();

		// update box2D
		world.step(dt, 6, 2);

		// remove crystals
		Array<Body> bodies = cl.getBodiesToRemove();
		for(int i = 0; i < bodies.size; i++) {
			Body b = bodies.get(i);
			crystals.removeValue((Crystal) b.getUserData(), true);
			world.destroyBody(b);
			player.collectCrystal();
		}
		
		bodies.clear();
		
		player.update(dt);

		for (int i = 0; i < crystals.size; i++) {
			crystals.get(i).update(dt);
		}

	}

	public void render() {

		// clear screen
		Gdx.gl10.glClear(GL10.GL_COLOR_BUFFER_BIT);

		// set camera to follow player
		cam.position.set(player.getPosition().x * PPM + Game.V_WIDTH / 4, Game.V_HEIGHT / 2, 0);
		cam.update();
		
		// draw tile map
		tmr.setView(cam);
		tmr.render();

		// draw player
		sb.setProjectionMatrix(cam.combined);
		player.render(sb);

		// draw crystals
		for (int i = 0; i < crystals.size; i++) {
			crystals.get(i).render(sb);
		}
		
		// draw HUD
		sb.setProjectionMatrix(hudCam.combined);
		hud.render(sb);

		// draw box2d world
		if (debug) {
			b2dr.render(world, b2dCam.combined);
		}

	}

	public void dispose() {
	}

	private void createPlayer() {
		BodyDef bdef = new BodyDef();
		FixtureDef fdef = new FixtureDef();
		PolygonShape shape = new PolygonShape();

		// create player
		bdef.position.set(100 / PPM, 200 / PPM);
		bdef.type = BodyType.DynamicBody;
		bdef.linearVelocity.set(1f, 0);
		Body body = world.createBody(bdef);

		shape.setAsBox(13 / PPM, 13 / PPM);
		fdef.shape = shape;
		fdef.filter.categoryBits = B2DVars.BIT_PLAYER;
		fdef.filter.maskBits = B2DVars.BIT_RED | B2DVars.BIT_CRYSTAL;
		body.createFixture(fdef).setUserData("player");

		// create foot sensor
		shape.setAsBox(13 / PPM, 2 / PPM, new Vector2(0, -13 / PPM), 0);
		fdef.shape = shape;
		fdef.filter.categoryBits = B2DVars.BIT_PLAYER;
		fdef.filter.maskBits = B2DVars.BIT_RED;
		fdef.isSensor = true;
		body.createFixture(fdef).setUserData("foot");

		// create player
		player = new Player(body);

		body.setUserData(player);
		
		shape.dispose();

	}

	private void createTiles() {

		// load tile map
		tileMap = new TmxMapLoader().load("res/maps/test.tmx");
		tmr = new OrthogonalTiledMapRenderer(tileMap);
		tileSize = (int) tileMap.getProperties().get("tilewidth", Integer.class);

		TiledMapTileLayer layer;

		layer = (TiledMapTileLayer) tileMap.getLayers().get("red");
		createLayer(layer, B2DVars.BIT_RED);

		layer = (TiledMapTileLayer) tileMap.getLayers().get("green");
		createLayer(layer, B2DVars.BIT_GREEN);

		layer = (TiledMapTileLayer) tileMap.getLayers().get("blue");
		createLayer(layer, B2DVars.BIT_BLUE);

	}

	private void createLayer(TiledMapTileLayer layer, short bits) {

		BodyDef bdef = new BodyDef();
		FixtureDef fdef = new FixtureDef();

		// go through all the cells in the layer
		for (int row = 0; row < layer.getHeight(); row++) {
			for (int col = 0; col < layer.getWidth(); col++) {

				// get cell
				Cell cell = layer.getCell(col, row);

				// check if cell exists
				if (cell == null)
					continue;
				if (cell.getTile() == null)
					continue;

				// create a body + fixture from cell
				bdef.type = BodyType.StaticBody;
				bdef.position.set((col + 0.5f) * tileSize / PPM, (row + 0.5f) * tileSize / PPM);

				ChainShape cs = new ChainShape();
				Vector2[] v = new Vector2[3];
				v[0] = new Vector2(-tileSize / 2 / PPM, -tileSize / 2 / PPM);
				v[1] = new Vector2(-tileSize / 2 / PPM, tileSize / 2 / PPM);
				v[2] = new Vector2(tileSize / 2 / PPM, tileSize / 2 / PPM);
				cs.createChain(v);
				fdef.friction = 0;
				fdef.shape = cs;
				fdef.filter.categoryBits = bits;
				fdef.filter.maskBits = B2DVars.BIT_PLAYER;
				fdef.isSensor = false;
				world.createBody(bdef).createFixture(fdef);
				
				cs.dispose();
			}
		}
		
	}

	private void createCrystals() {
		crystals = new Array<Crystal>();
		MapLayer layer = tileMap.getLayers().get("crystals");

		BodyDef bdef = new BodyDef();
		FixtureDef fdef = new FixtureDef();

		for (MapObject mo : layer.getObjects()) {
			bdef.type = BodyType.StaticBody;

			float x = (float) mo.getProperties().get("x", float.class) / PPM;
			float y = (float) mo.getProperties().get("y", float.class) / PPM;

			bdef.position.set(x, y);

			CircleShape cshape = new CircleShape();
			cshape.setRadius(8 / PPM);

			fdef.shape = cshape;
			fdef.isSensor = true;
			fdef.filter.categoryBits = B2DVars.BIT_CRYSTAL;
			fdef.filter.maskBits = B2DVars.BIT_PLAYER;

			Body body = world.createBody(bdef);
			body.createFixture(fdef).setUserData("crystal");

			Crystal c = new Crystal(body);
			crystals.add(c);

			body.setUserData(c);
			
			cshape.dispose();

		}
	}
	
	private void switchBlocks() {
		Filter filter = player.getBody().getFixtureList().first().getFilterData();
		short bits = filter.maskBits;
		
		// switch to next color
		// red > green > blue > red
		if((bits & B2DVars.BIT_RED) != 0) {
			bits &= ~B2DVars.BIT_RED;
			bits |= B2DVars.BIT_GREEN;
		} else if((bits & B2DVars.BIT_GREEN) != 0) {
			bits &= ~B2DVars.BIT_GREEN;
			bits |= B2DVars.BIT_BLUE;
		} else if((bits & B2DVars.BIT_BLUE) != 0) {
			bits &= ~B2DVars.BIT_BLUE;
			bits |= B2DVars.BIT_RED;
		}
		
		// set new mask bits
		filter.maskBits = bits;
		player.getBody().getFixtureList().first().setFilterData(filter);
		
		// set new mask bits for foot
		filter = player.getBody().getFixtureList().get(1).getFilterData();
		bits &= ~B2DVars.BIT_CRYSTAL;
		filter.maskBits = bits;
		player.getBody().getFixtureList().get(1).setFilterData(filter);
	}

}
