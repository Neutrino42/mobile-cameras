package mobileCameras;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import repast.simphony.context.Context;
import repast.simphony.context.space.continuous.ContinuousSpaceFactory;
import repast.simphony.context.space.continuous.ContinuousSpaceFactoryFinder;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.continuous.RandomCartesianAdder;

public class MyTests {

	private static Context<Object> context;
	private static ContinuousSpace<Object> space;
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		context = new MyContext();

		context.setId("mobileCameras");
		ContinuousSpaceFactory spaceFactory = 
				ContinuousSpaceFactoryFinder.createContinuousSpaceFactory(null);
		space = 
				spaceFactory.createContinuousSpace("space", context, 
						new RandomCartesianAdder<Object>(),
						new repast.simphony.space.continuous.BouncyBorders(),
						50, 50);
		
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void test() {
		double speed = 10;
		int angleDeg = 270;
		Human human = new Human(111, space, null, angleDeg, speed);
		context.add(human);
		space.moveTo(human, 1, 1);
		
		
		int newAngle = MyUtil.returnBounceAngle(space, space.getLocation(human), angleDeg, speed);
		assertEquals(90, newAngle);
		
		angleDeg = 120;
		newAngle = MyUtil.returnBounceAngle(space, space.getLocation(human), angleDeg, speed);
		assertEquals(60, newAngle);
		
		angleDeg = 240;
		newAngle = MyUtil.returnBounceAngle(space, space.getLocation(human), angleDeg, speed);
		assertEquals(60, newAngle);
		
		angleDeg = 240;
		newAngle = MyUtil.returnBounceAngle(space, space.getLocation(human), angleDeg, speed);
		assertEquals(60, newAngle);
		
		
	}

}
