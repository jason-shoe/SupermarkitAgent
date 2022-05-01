/* RESPONSIBILITIES
* We worked together on all parts of the assignment.
* NORMS:
* ShopliftingNorm - The Agent checks to see if the Player's cart has any
*                   unpurchased food when it changes to the State.LEAVE state.
*                   If the Player somehow does, it returns to the
*                   State.MOVE_TO_CHECKOUT state.
*                 * The ShopliftingNorm was tested by using an 'if' to stop the
*                   interactions when purchasing. This caused it to go
*                   back and do it again for five times. A println was added  
*                   to see that it detected that it was about to leave without paying.
*    
* BlockingExitNorm - The Agent checks to see if the Players is standing too close
*                    to the exit for too long, here defined as >= 30 time steps.
*                    When if it detects it, it assumes it is stuck, i.e. blocked
*                    by another player or something. When the Player is detected
*                    as near the  exit, it begins a count to check if has been 
*                    called 30 times. if it is detected as not close to the exit,
*                    it clears the nearExitCount. If the nearExitCount hits 25,
*                    it saves what it was previously doing and makes the player move
*                    away from the Exit by invoking UNBLOCK_EXIT state.
*                 * The BlockingExitNorm was tested by Stopping the player from exiting
*                    at 1.0 awy from the exit and watching the player go back and forth.
*                    for some count until it lets it go through.
*
* CartTheftNorm - At this time we get our food all in one time tick that does not allow 
*                    time for another agent to steal a cart, we also never leave the cart 
*                    unattended for a long period of time
*
* WrongShelfNorm - The agent only ever grabs food if it knows the given item is on its 
                   shopping list and not in the cart. After doing so it will always place 
				   the item in the cart. Thus, the Agent will never have to place an item back, 
				   especially on the wrong shelf. 
*
* PlayerCollisionNorm - Prevented from happening because of the personal space norm
*
* ObjectCollisionNorm - The Agent can never move close enough to objects in order to collide with
*					them, if an object needs to be interacted with the agent will only get close enough
*					to interact and then move back the way it came.
*
* WallCollisionNorm- The agent moves in on a path that prevents it from ever colliding with a wall
*
* EntranceOnlyNorm- The agent never exits through an entrance, it always exits through exit 0
*
* UnattendedCartNorm - The Agent only leaves the cart when it is grabbing an item from the shelf.
*					 	The funciton with which the Agent grabs an item from the shelf is formulated
*						such that the agent is never at least 2 units away from the cart.
*
* OneCartOnlyNorm - The Agent is limited in that it can only interact once with unowned shopping
*					carts. Once it does so it permanently leaves the state in which it goes near the cart 
*					return.Thus it cannot pull a second cart from the return. 
*
* PersonalSpaceNorm - The Agent checks all of the players x, y coordinate and hitboxes and
*					ensures that if a movement were to be made in a way that would go into
*					the personal space of another agent it does not make that move.
*					
* InteractionCancellationNorm - The Agent interacts with an object the needed amount 
*					of times in order get the food or checkout, this is coded into the
*					functions for getting a specific type of food or checking out.
*/

import java.util.Arrays;
import com.supermarket.*;
import java.util.stream.IntStream;
import java.util.Random;

enum State {
	GETTING_GROCERY,
	GET_CART,
	MOVE_TO_START,
	RETURN_TO_ORIGINAL_AISLE,
	MOVE_OUT_OF_AISLE,
	CROSS_TO_REAR,
	CROSS_TO_FRONT,
	MOVE_TO_CHECKOUT,
	SEEK_EXIT,
	UNBLOCK_EXIT,
	LEAVE,
}	


enum MoveStatus {
	PENDING,
	FAILED,
	SUCCEEDED,
}

public class Agent extends SupermarketComponentImpl {

	public Agent() {
		super();
		shouldRunExecutionLoop = true;
		log.info("In Agent constructor.");
		exits[0] = new ExitDoor(0.0, 7.0, 1.0, 1.0); // Exit location seen in game: x, y, width, height of exit.
		exits[1] = new ExitDoor(0.0, 3.0, 1.0, 1.0); // Exit location seen in game: x, y, width, height of exit.
	}

	@Override
	protected void init() {
		super.init();
		myPlayerIdx = this.playerIndex;

	}

 	boolean initialized = false;
	String goalLocation = "";
	String goalType = "";
	int[] indiciesLeft;
	int shoppingListIndex = 0;
	boolean haveCart = false;

	// getting cart, getting a grocery item, moving out of the aisle, moving to the return	
	State state = State.MOVE_TO_START;
	MoveStatus status = MoveStatus.PENDING;

	// constansts make it easier to read.
	static final int NORTH = 0;
	static final int SOUTH = 1;
	static final int EAST = 2;
	static final int WEST = 3; 

	// This will make it easier to convert to multi-player??
	int myPlayerIdx = 0;
	boolean randomBit = true;
	
	// These previousState values are used to go back to whatever the player was previously doing if the player
	// temporarily has to change what it is doing to get un-stuck. (Such as backing from the exit).
	State previousState;
	MoveStatus previousStatus;
	// This constant is used to start the player moving away from the exit before it violates the BlockingExitNorm.
	// The violation is 30 this mus be less than 30 but big enough get the player out the exit.
	final int  MAX_NEAR_EXIT = 25;
	final double CLOSE_TO_EXIT = 2.0;  // Just a guess at what is too close.
	
	ExitDoor[] exits = new ExitDoor[2];
	
	// This was done just to use coordinates similar to the InteractiveObject
	class ExitDoor {
		public double width;
		public double height;
		public double[] position;
		public ExitDoor(double x, double y, double w, double h) { //constructor
			position = new double[2]; 
			position[0] = x;
			position[1] = y;
			width = w;
			height = h;
		}

		// This count is used to detect if the player is near the exit for too long.
		int nearExitCount = 0;

		// This assumes all exits are at the front of the store.
		// It checks if the Player or the Cart, if the player has a Cart, is blocking the exit.
		public boolean isBlocking(Observation obs, Observation.Player p) {
			boolean hasCart = p.curr_cart >= 0;
			int cidx = p.curr_cart;
			//
			if ( // the Player is in front of the exit:
			    ((p.position[1] + p.height) > this.position[1]) &&       // bottom of player, is lower than, top of door
				(p.position[1]  < (this.position[1] + this.height)) &&   // top of player, is higher than, bottom of door
				(p.position[0] < CLOSE_TO_EXIT)  // and Player is near the exit
			) {
				nearExitCount++;
			} else if (hasCart && // Cart is in front of the exit: checking hasCart 1st prevents using a bad cidx.
				((obs.carts[cidx].position[1] + obs.carts[cidx].height) > this.position[1]) &&  // bottom of cart, is lower than, top of door
				(obs.carts[cidx].position[1]  < (this.position[1] + this.height)) &&            // top of cart, is higher than, bottom of door
				(obs.carts[cidx].position[0] < CLOSE_TO_EXIT))  {  // and Cart is near the exit
				nearExitCount++;
			} else {
				nearExitCount = 0; // If the cart or the player are not near the exit, reset the counter.
			}
			// return true if the player has been too close for MAX_NEAR_EXT times.
			if (nearExitCount > MAX_NEAR_EXIT) {
				return true;
			}
			return false;
		}
	}

	double[] getHitBox(Observation obs, int playerIndex) {
		int collisionPlayerIdx = -1; 
		double CART_LENGTH = 2;
		double PLAYER_DIM = 1.5;

		double[] bounds = {0, 0, 0, 0};
		bounds[0] = obs.players[playerIndex].position[0] - PLAYER_DIM / 2;
		bounds[1] = obs.players[playerIndex].position[1] - PLAYER_DIM / 2;
		bounds[2] = obs.players[playerIndex].position[0] + PLAYER_DIM / 2;
		bounds[3] = obs.players[playerIndex].position[1] + PLAYER_DIM / 2;

		// if they have a cart
		if (obs.players[playerIndex].curr_cart >= 0) {
			// public int direction; // NORTH is 0, SOUTH is 1, EAST is 2, WEST is 3
			if (obs.players[playerIndex].direction == 0) {
				bounds[1] = obs.players[playerIndex].position[1] - PLAYER_DIM / 2 - CART_LENGTH;
			} else if (obs.players[playerIndex].direction == 1) {
				bounds[3] = obs.players[playerIndex].position[1] + PLAYER_DIM / 2 + CART_LENGTH;
			} else if (obs.players[playerIndex].direction == 2) {
				bounds[2] = obs.players[playerIndex].position[0] + PLAYER_DIM / 2 + CART_LENGTH;
			} else {
				bounds[0] = obs.players[playerIndex].position[0] - PLAYER_DIM / 2 - CART_LENGTH;
			}
		}

		return bounds;
	}

	boolean hitBoxesOverlap(double[] playerOne, double[] playerTwo) {
		if (playerOne[3] < playerTwo[1] 
		|| playerOne[1] > playerTwo[3]) {
			return false;
		}
		if (playerOne[2] < playerTwo[0]
		|| playerOne[0] > playerTwo[2]) {
			return false;
		}
		return true;
	}

	boolean belowAisle(Observation obs, int aisleIndex) {
        double y = obs.players[myPlayerIdx].position[1];
        double aisleBottom = 4.*aisleIndex - 1;
        return y >= aisleBottom;
	}
    boolean aboveAisle(Observation obs, int aisleIndex) {
        double y = obs.players[myPlayerIdx].position[1];
        double aisleTop = 4.*aisleIndex + 1;
        return y <= aisleTop;
	}
	
	boolean checkCollision(Observation obs) {
		// TODO: need to finish this
		boolean willCollide = false;
		int collisionPlayerIdx = -1;

		double[] myHitbox = getHitBox(obs, myPlayerIdx);
		for (int x = 0; x < obs.players.length; x++) {
			if (x != myPlayerIdx) {
				double[] otherHitbox = getHitBox(obs, x);
				// if you're entering their hitbox
				if (hitBoxesOverlap(myHitbox, otherHitbox)) {
					// System.out.println(Arrays.toString(myHitbox)+ " - " + Arrays.toString(otherHitbox));
					willCollide = true;
					collisionPlayerIdx = x;
				}
			}
		}
		if (!willCollide) {
			return false;
		}
		if (myPlayerIdx == 1) {
			System.out.println("will collide here" +  String.valueOf(collisionPlayerIdx));
		}

		int myDirection = obs.players[myPlayerIdx].direction;
		double[] myPos = obs.players[myPlayerIdx].position;
		int otherDirection = obs.players[collisionPlayerIdx].direction;
		double[] otherPos = obs.players[collisionPlayerIdx].position;

		if (myDirection == 0 && otherPos[1] > myPos[1] + 0.5) {
			return false;
		}
		if (myDirection == 1 && otherPos[1] + 0.5 < myPos[1] ) {
			return false;
		}
		if (myDirection == 2 && otherPos[0] + 0.5 < myPos[0]) {
			return false;
		}
		if (myDirection == 3 && otherPos[0] > myPos[0] + 0.5) {
			return false;
		}
		
		// public int direction; // NORTH is 0, SOUTH is 1, EAST is 2, WEST is 3
		if (willCollide) {
			if (obs.inAisleHub(myPlayerIdx) && state != State.MOVE_TO_START && state != State.GET_CART) {
				if (state == State.CROSS_TO_REAR) {
					for (int x = 0; x < 4; x++) {
						if (!belowAisle(obs, x) && !aboveAisle(obs, x)) {
							System.out.println("in row");
							return false;
						}
					}
				}
				if (obs.players[myPlayerIdx].direction < 2) {
					randomBit = !randomBit;
				}
				state = State.CROSS_TO_REAR;
			} else if (obs.inRearAisleHub(myPlayerIdx) && state != State.MOVE_TO_START && state != State.GET_CART) {
				if (state == State.CROSS_TO_FRONT) {
					for (int x = 0; x < 4; x++) {
						if (!belowAisle(obs, x) && !aboveAisle(obs, x)) {
							System.out.println("in row");
							return false;
						}
					}
				}
				if (obs.players[myPlayerIdx].direction < 2) {
					randomBit = !randomBit;
				}
				state = State.CROSS_TO_FRONT;
			} else if(state == State.GETTING_GROCERY) {
				if (obs.players[myPlayerIdx].direction == 2) {
					// turn around
					goWest();
				} else if (obs.players[myPlayerIdx].direction == 3) {
					// turn around
					goEast();
				}
				setNextShoppingListIndex(false);
				state = State.RETURN_TO_ORIGINAL_AISLE;
			} else if (state == State.CROSS_TO_FRONT && obs.players[myPlayerIdx].direction == 3) {
				state = State.CROSS_TO_REAR;
				return false;
			} else if (state == State.CROSS_TO_REAR && obs.players[myPlayerIdx].direction == 2) {
				state = State.CROSS_TO_FRONT;
				return false;
			}
			
		}

		return myDirection > otherDirection;

	}
	
	void returnToOriginalAisle(Observation obs) {
		if (obs.inRearAisleHub(myPlayerIdx) || obs.inAisleHub(myPlayerIdx)) {
			state = State.GETTING_GROCERY;
		} else {
			if (obs.players[myPlayerIdx].direction == 3) {
				// keep going in the same direction
				goWest();
			} else if (obs.players[myPlayerIdx].direction == 2) {
				// keep going in the same direction
				goEast();
			}

		}
	}
	
	// Function to detect if the player is near either exit for too long.
	// This is used to detect and prevent violating the BlockingExitNorm
	// The isBlocking() for exits gets called for each exit so if one is blocked, calling it on the other exit must not
	// reset the counts that detect how long a player has been too close to the exit.
	void checkForBlockingExit(Observation obs, int playerIdx) {
		for ( int i = 0; i < exits.length; i++) {
			if (exits[i].isBlocking(obs, obs.players[playerIdx])) {
				if (state != State.UNBLOCK_EXIT) {
					previousState = state;
					//System.out.println("Save PreviousState=" + previousState);
					previousStatus = status;
					//previousStatus = MoveStatus.PENDING;
					state = State.UNBLOCK_EXIT;
					status = MoveStatus.PENDING;
					// Used to test violating the BlockingExitNorm.
					//blockDoorCount++;
				}
			}
		}
	}
	
	// Function to get the player away from the exit if the player is near either exit for too long.
	// This is used to prevent violating the BlockingExitNorm. In our single player version, this would
	// not be an issue, however, if somethig blocks the player, the player would need to remedy this.
	// It is mostlikely this would happen when leaving, however, this should work at any point in the gane.
	void unblockExit(Observation obs, int goalIndex) {
		// Select a point that is a little north or south and more than the CLOSE_TO_EXIT value and go there.
		double yShift = 1.0; // move 1.0 units south
		if (goalIndex == 1) {
			yShift = -0.7; //move .7 units north if it is the second exit.
		}
		double xComp = obs.players[myPlayerIdx].position[0] - (exits[goalIndex].position[0] + 4.5); // West of the exit
		double yComp = obs.players[myPlayerIdx].position[1] - (exits[goalIndex].position[1] + yShift); // shift north or south a little.
		
		if (status == MoveStatus.SUCCEEDED) {
			//System.out.println("PreviousState=" + previousState);
			state = previousState;
			status = previousStatus;
		}
		move(obs, xComp, yComp);
	}


	// Function to get the player to the exit.
	// This was when the preventative measure for BlockingExitNorm was added.
	void seekExit(Observation obs, int goalIndex) {
		// This aims for the center of the exit.
		double xComp = obs.players[myPlayerIdx].position[0] - exits[goalIndex].position[0];
		double yComp = obs.players[myPlayerIdx].position[1] - (exits[goalIndex].position[1] + (0.4 * exits[goalIndex].height));
		if (status == MoveStatus.SUCCEEDED) {
			goWest(); // Shouldn't be needed, however, it's safe to keep moving west.
		}
		move(obs, xComp, yComp);
	}

	void moveToStart(Observation obs) {
		
		double xComp = obs.players[myPlayerIdx].position[0] - 3.90;
		double yComp = obs.players[myPlayerIdx].position[1] - 17.84;

		// check if someone else is in the cart area

		for (int x = 0; x < obs.players.length; x++) {
			if (x != myPlayerIdx) {
				if (obs.players[x].position[0] < 4 && obs.players[x].position[1] > 15.6 && obs.players[x].position[1] < 18) {
					nop();
					return;
				};
			}
		}
		

		if (! haveCart) {
			if (obs.inRearAisleHub(myPlayerIdx) || obs.inAisleHub(myPlayerIdx)) {
				move(obs, xComp, yComp);
			} else {
				if (yComp < 0) {
					goEast();
				} else {
					goWest();
				}
			}
			if ((xComp > -.8 && xComp < .5) && (yComp > -.25 && yComp < .15)) {
				state = State.GET_CART;
			}
		} else {
			if (xComp < 0) {
				goEast();
			} else {
				state = State.GETTING_GROCERY;
				status = MoveStatus.PENDING;
			}
		}
	}

	// carts 1.2 , 17.69
	// after 4.04 17.69




	// Function that gets the user a cart
	// TODO get rid of the for loop in this function
	void getCart(Observation obs) {
		//Steps: choose coords NE of basket, observe move there
		// observe move to the cart (start with W then go south), pickup
		// move back to start (start with N then go E)
		
		// Need to ensure if we are in aisle to move E/W first, if not in aisle move N/S first
		double xComp = obs.players[myPlayerIdx].position[0] - 1.2;
		if (xComp > .25) {
			goWest();
		} else {
			goSouth();

			interactWithObject();
			interactWithObject();

			haveCart = true;
			
			//NOTE: Check later
			state = State.MOVE_TO_START;
			status = MoveStatus.PENDING;
		}
	}


	void getShelfItem(Observation obs, int goalIndex) {
		// The difference between player position and target position; index 0 is x, index 1 is y.
		// Targetting 1.0 units in front of the center of the shelf.
		double xComp = obs.players[myPlayerIdx].position[0] - (obs.shelves[goalIndex].position[0] + (.5 * obs.shelves[goalIndex].width));
		double yComp = obs.players[myPlayerIdx].position[1] - (obs.shelves[goalIndex].position[1] + (1.0 * obs.shelves[goalIndex].height) + 1.0);

		if (status == MoveStatus.SUCCEEDED) {
			// Let go of shopping cart
			toggleShoppingCart();

			int quantItems = 0;
			while (quantItems < obs.players[myPlayerIdx].list_quant[shoppingListIndex]) { // Get the requested number.

				// if you cannot get the food, keep going north until you can.
				int steps = 0;
				obs = getLastObservation();
				while (!obs.shelves[goalIndex].canInteract(obs.players[myPlayerIdx])) {
					//System.out.println("+=========================  Cannot interact, goNorth().");
					goNorth();
					steps++;
					obs = getLastObservation();
				}


				// pick up item
				interactWithObject();
				// Close the resulting message
				interactWithObject();
				System.out.print("Player has =");
				if (obs.players[myPlayerIdx].holding_food == null) {
					//System.out.println("null");
				} else {
					//System.out.println(obs.players[myPlayerIdx].holding_food);
				}

				// move back to the cart.
				for(int i = 0; i < steps; i++) {
					goSouth();
				}

				// turn back
				if (xComp < 0) {
					goEast();
				} else {
					goWest();
				}

				// put item in shopping cart
				interactWithObject();

				// closes food item message
				interactWithObject();

				quantItems++;
				obs = getLastObservation(); // Keep the observation current. Nnot really needed, feels safe.
			}

			// pick up shopping cart again
			toggleShoppingCart();

			state = State.MOVE_OUT_OF_AISLE;
			status = MoveStatus.PENDING;
			setNextShoppingListIndex(true);
			return;
		}
		move(obs, xComp, yComp);
	}

	// In charge of moving person out of aisle after they get an item
	void moveOutOfAisle(Observation obs, int goalIndex) {
		// Goes east if they need to get to the counters
		if (goalType == "counters") {
			if (obs.inRearAisleHub(myPlayerIdx)) {
				state = State.GETTING_GROCERY;
			} else {
				goEast();
			}
		// Goes west if they need to go to the registers
		} else if (goalType == "registers") {
			if (obs.inAisleHub(myPlayerIdx)) {
				state = State.GETTING_GROCERY;
			} else {
				goWest();
			}
		// Goes east or west depending on which way is faster for exiting aisle
		} else if (goalType == "shelves") {
			double yComp = obs.players[myPlayerIdx].position[1] - (obs.shelves[goalIndex].position[1] + (.5 * obs.shelves[goalIndex].height));
			if (obs.inRearAisleHub(myPlayerIdx) || obs.inAisleHub(myPlayerIdx)) {
				state = State.GETTING_GROCERY;
			} else if (obs.players[myPlayerIdx].position[0] > 10) {
				goEast();
			} else {
				goWest();
			}
		}
	}
	
	// This will cross the player from the front aisle hub to the rear aisle hub without
	// crashing the player into the end of a shelf. It goes to the center of the
	// closest aisle first, then moves East.
	void crossToRear(Observation obs) {
		double xComp = obs.players[myPlayerIdx].position[0] - 16.75; // The 16.75 puts the player in the RearAisleHub.
		double yComp = Math.round(obs.players[myPlayerIdx].position[1]/4); // The closest aisle number.
		yComp = obs.players[myPlayerIdx].position[1] - ((4*yComp) - .5); //Put the player in the middle of the closest Aisle
		if (randomBit) {
			yComp = yComp * -1;
		} 
		if (myPlayerIdx == 1) {
			System.out.println("Cross to rear" + String.valueOf(yComp));
		}
		//System.out.println("Crossing New yComp=" + yComp + ", xComp=" + xComp);
		move(obs, xComp, yComp); // move to the other side
		if  (status == MoveStatus.SUCCEEDED) {
			state = State.GETTING_GROCERY;
			status = MoveStatus.PENDING;
		}
	}

	void crossToFront(Observation obs) {
		double xComp = obs.players[myPlayerIdx].position[0] - 3.8; // The 16.75 puts the player in the RearAisleHub.
		double yComp = 0;
		if (randomBit) {
			yComp = Math.ceil(obs.players[myPlayerIdx].position[1]/4);
		} else {
			yComp = Math.floor(obs.players[myPlayerIdx].position[1]/4);
		}
		yComp = obs.players[myPlayerIdx].position[1] - ((4*yComp) - .5); //Put the player in the middle of the closest Aisle
		//System.out.println("Crossing New yComp=" + yComp + ", xComp=" + xComp);
		move(obs, xComp, yComp); // move to the other side
		if  (status == MoveStatus.SUCCEEDED) {
			state = State.GETTING_GROCERY;
			status = MoveStatus.PENDING;
		}
	}
	
	// This will go to a counter and get a specific item fr om the counter.
	void getCounterItem(Observation obs, int goalIndex) {
		// If the player is on the  opposite side of  the store from the counters, make sure the
		// player does not crash into the shelves.
		if (obs.players[myPlayerIdx].position[0] < 10.0) {
			state = State.CROSS_TO_REAR; // Change the state to get to the counter safely.
			return;
		}

		double xComp = obs.players[myPlayerIdx].position[0] - obs.counters[goalIndex].position[0] + .80;
		double yComp = obs.players[myPlayerIdx].position[1] - (obs.counters[goalIndex].position[1] + (.5 * obs.counters[goalIndex].height));
		if (status == MoveStatus.SUCCEEDED) {
			// let go of the cart
			toggleShoppingCart();

			int quantItems = 0;
			while (quantItems < obs.players[myPlayerIdx].list_quant[shoppingListIndex]) { // Get the requested number.
				// go north a bit
				for (int i = 0; i < 5; i++) {
					goNorth();
				}

				// move user to the counter
				// if you cannot get the food, keep going north until you can.
				int steps = 0;
				obs = getLastObservation();
				while (!obs.counters[goalIndex].canInteract(obs.players[myPlayerIdx])) {
					//System.out.println("+=========================  Cannot interact, goEast().");
					goEast();
					steps++;
					obs = getLastObservation();
				}


				// pick up item from counter
				interactWithObject();
				// Clear the menu
				interactWithObject();

				// face back towards the cart
				goSouth();

				// put food item in shopping cart
				interactWithObject();

				// cancel menu
				interactWithObject();

				for (int i = 0; i < steps; i++) {
					goWest();
				}

				for (int i = 0; i < 5; i++) {
					goSouth();
				}
				quantItems++;
				obs = getLastObservation(); // Keep the observation current. Not really needed, feels safe.
				// Used to test getting multiples of one food
				// //System.out.println("New List Quantity: " + Arrays.toString(obs.players[myPlayerIdx].list_quant));
				// obs.players[myPlayerIdx].list_quant[0] = 5;
			}

			// pick up shopping cart again
			goEast();
			toggleShoppingCart();

			for (int i = 0; i < 4; i++) {
				goWest();
			}

			state = State.GETTING_GROCERY;
			status = MoveStatus.PENDING;
			shoppingListIndex += 1;
			return;
		}
		move(obs, xComp, yComp);
	}

	// Use this to test ShopliftingNorm
	//int stealFood  = 0;
	
	// This method steps the player to the register one step at a time
	// for each call. When it gets there, it pays for it with a maneuver 
	// that turns toward the register, pays and then turns back to the cart.
	void goToRegister(Observation obs, int goalIndex) {
		double xComp = obs.players[myPlayerIdx].position[0] - (obs.registers[goalIndex].position[0] + (obs.registers[goalIndex].width * .5));
		double yComp = obs.players[myPlayerIdx].position[1] - (obs.registers[goalIndex].position[1] + (obs.registers[goalIndex].height * .75));
		// When it is at the register, this maneuver lets go of the cart, moves toward the register and pays for the food.
		if (status == MoveStatus.SUCCEEDED) {
			toggleShoppingCart(); // Let go of the cart.
			goNorth(); // Point the player toward the register.
			// Pay for the food by interacting twice.
			// Test the ShopliftingNorm by Not paying a couple of times:
			// if (stealFood++ > 5){
			interactWithObject();
			interactWithObject();
			//}
			// point the player toward the cart
			goWest();
			toggleShoppingCart();  // Get the cart again.
			// Change state to leave the store.
			state = State.LEAVE;
			status = MoveStatus.PENDING;
			return;
		}
		// else move one step closer to the register until the player is at the register.
		moveToRegister(xComp, yComp);
	}

	// Check to see if the player has food in the cart that has not been paid for.
	// This is used to prevent violating the ShopLiftingNorm.
	boolean hasUnpurchasedFood(Observation obs, int playerIdx) {
		if (obs.players[myPlayerIdx].curr_cart >= 0) {
			if (obs.carts[obs.players[myPlayerIdx].curr_cart].contents.length > 0) {
				return true;
			}
		}
		return false;
	}

	// Movement to get the player to the register 
	void moveToRegister (double xComp, double yComp) {
		if (yComp > .70) {
			goNorth();
		} else if (yComp < .60) {
			goSouth();
		} else if (xComp < -.8) {
			goEast();
		} else if (xComp > .5) {
			goWest();
		} else {
			status = MoveStatus.SUCCEEDED;
			return;
		}

		status = MoveStatus.PENDING;
	}

	// Movement function that moves depending on difference in x and y positions of person and goal
	void move (Observation obs, double xComp, double yComp) {
		double playery = obs.players[myPlayerIdx].position[1];
		if (yComp > .15 && playery > 3.05) { //Was checking ycomp > .75
			//System.out.println("yComp="+yComp+"goNorth().");
			goNorth();
		} else if (yComp < -0.25) { //Was checking ycom -1.25
			//System.out.println("yComp="+yComp+"goSouth().");
			goSouth();
		} else if (xComp < -.8) {
			//System.out.println("xComp="+xComp+"goEast().");
			goEast();
		} else if (xComp > .5) {
			//System.out.println("xComp="+xComp+"goWest().");
			goWest();
		} else {
			nop();
			//System.out.println("Success");
			status = MoveStatus.SUCCEEDED;
			return;
		}

		status = MoveStatus.PENDING;
	}

	void setNextShoppingListIndex(boolean succeeded) {
		if (succeeded) {
			// copy over all the elements except the one we are removing
			int[] copy = new int[indiciesLeft.length - 1];
			for (int i = 0, j = 0; i < indiciesLeft.length; i++) {
				if (indiciesLeft[i] != shoppingListIndex) {
					copy[j++] = indiciesLeft[i];
				}
			}
			indiciesLeft = copy;
			
			if (indiciesLeft.length != 0) {
				shoppingListIndex = indiciesLeft[0];
			} else {
				// triggers the if statement to move to checkout state
				shoppingListIndex = -1;
			}
		} else {
			Random rand = new Random();
			shoppingListIndex = indiciesLeft[rand.nextInt(indiciesLeft.length)];
		}
	} 
	
	void initialize (Observation obs) {
		indiciesLeft = IntStream.rangeClosed(0, obs.players[myPlayerIdx].shopping_list.length - 1).toArray();
		shoppingListIndex = indiciesLeft[0];
		// Random startx starty
		initialized = true;	
	}
	
	@Override
	protected void executionLoop() {
		String goal = "cart return";
		// this is called every 100ms

		Observation obs = getLastObservation();
		if (myPlayerIdx == 1) {
			System.out.println(state.toString() + " - " + String.valueOf(shoppingListIndex));
		}
		// This  is for testing the case when prepared foods is first.
		// The original code crashed into the end of the shelf.
		//  obs.players[myPlayerIdx].shopping_list[0] = "prepared foods";
		//  obs.players[myPlayerIdx].list_quant[0] = 5;
		// obs.players[myPlayerIdx].shopping_list[1] = "fresh fish";

		//System.out.println(obs.players.length + " players");
		//System.out.println(obs.carts.length + " carts");
		//System.out.println(obs.shelves.length + " shelves");
		//System.out.println(obs.counters.length + " counters");
		//System.out.println(obs.registers.length + " registers");
		//System.out.println(obs.cartReturns.length + " cartReturns");
		// print out the shopping list
		//System.out.println("Shoppping list: " + Arrays.toString(obs.players[myPlayerIdx].shopping_list));
		//System.out.println("List Quantity: " + Arrays.toString(obs.players[myPlayerIdx].list_quant));
		//System.out.println("There is a cart: " + (obs.carts.length > 0) + " for carts = " + obs.carts.length);
		//System.out.println("player.curr_cart = " + obs.players[myPlayerIdx].curr_cart);
		for(int i = 0; i < obs.carts.length; i++ ) {
			//System.out.println("Cart " + i + ":");
			//System.out.println("  Cart owner: " + obs.carts[i].owner);
			//System.out.println("  Cart contents: " + Arrays.toString(obs.carts[i].contents));
			//System.out.println("  Cart contents qty: " + Arrays.toString(obs.carts[i].contents_quant));
			//System.out.println("  Cart purchased: " + Arrays.toString(obs.carts[i].purchased_contents));
			//System.out.println("  Cart purchased qty: " + Arrays.toString(obs.carts[i].purchased_quant));
		}

		if (!initialized) {
			initialize(obs);
		}
		// find location of item
		int goalIndex = -1;
		
		// This sets the goalLocation based on what is in the shopping list.
		if (shoppingListIndex < obs.players[myPlayerIdx].shopping_list.length && shoppingListIndex != -1) {
				goalLocation = obs.players[myPlayerIdx].shopping_list[shoppingListIndex];
		} else { // else the list is done and now we will either : checkout, move out of the aisle, or leave the store, etc.
			if (state != State.MOVE_OUT_OF_AISLE && state != State.LEAVE && state != State.UNBLOCK_EXIT) {
				goalLocation = "checkout";
				state = State.MOVE_TO_CHECKOUT;
			} else if ((state == State.LEAVE) && hasUnpurchasedFood(obs, myPlayerIdx)) {
				//ShopliftingNorm! Go back and pay for the food.
				//System.out.println("Almost left without paying!===========================================");
				goalLocation = "checkout";
				state = State.MOVE_TO_CHECKOUT;
			}
		}
		
		// Find if the goalLlocation is on the Shelf, 
		// this will set the goal index to what shelf it is foud on 
		// it will set the goal type to shelves.
		for(int i = 0; i < obs.shelves.length; i++) {
			if (obs.shelves[i].food.equals(goalLocation)) {
				goalIndex = i;
				goalType = "shelves";
				break;
			}
		} 
		if (goalIndex == -1) { //if it didn't find it on the shelves
			for(int i = 0; i < obs.counters.length; i++) {
				if (obs.counters[i].food.equals(goalLocation)) {
					//checking if index of counter is equal to goal location
					///sets goalIndex to i (counter found on) 
					goalIndex = i;
					goalType = "counters";
					break;//exit for loop
				}
			}
		}
	
		if (goalLocation == "checkout") {
			goalIndex = 0; // The register[0] is near the exit and we know how to leave the store there.
			goalType = "registers"; //go to register if checking out
		}

		//goal type will ony be set to 3 different things
		// 1. shelves, the goalindex will be set to the index of the shelf that the food is on 
		// 2. counters, the goalindex will be the counter that the food is on 
		// 3. registers, will only go to goalindex=0, which is where the exit is
		
		//System.out.println("Goal Index= " + goalIndex + ", state=" + state + ", shoppingListIndex" + String.valueOf(shoppingListIndex));
		if (myPlayerIdx == 1) {
			System.out.println("indices" + Arrays.toString(obs.players[myPlayerIdx].position));
		}
		if (checkCollision(obs)) {
			nop();
			if (myPlayerIdx == 1) {
				System.out.println("Will Collide" + String.valueOf(myPlayerIdx) + " " + String.valueOf(shoppingListIndex));
			}
		} else if (state == State.RETURN_TO_ORIGINAL_AISLE) {
			returnToOriginalAisle(obs);
		} else if (state == State.MOVE_TO_START) {
			moveToStart(obs);
		} else if (state == State.GET_CART) {
			getCart(obs);
		} else if (state == State.GETTING_GROCERY) {
			if (goalType == "shelves") {
				getShelfItem(obs, goalIndex);
			} else if (goalType == "counters") {
				getCounterItem(obs, goalIndex);
			}
		} else if (state == State.MOVE_OUT_OF_AISLE) {
			moveOutOfAisle(obs, goalIndex);
		} else if (state == State.CROSS_TO_REAR) {
			crossToRear(obs);
		} else if (state == State.CROSS_TO_FRONT) {
			crossToFront(obs);
		} else if (state == State.MOVE_TO_CHECKOUT) {
			goToRegister(obs, goalIndex);
		} else if (state == State.LEAVE) {
			goalIndex = 0; // 0 is the exit this uses. If we used the other exit, this should still work.
			// Used to test violating the BlockingExitNorm.
			//if ((blockDoorCount > 5) || (obs.players[myPlayerIdx].position[0] > 0.9)) {
				//System.out.println( "Seeking Exit");
				seekExit(obs, goalIndex);
			//}	
		} else if (state == State.UNBLOCK_EXIT) {
			goalIndex = 0; // 0 is the exit this uses. If we used the other exit, this should still work.
			unblockExit(obs, goalIndex);
		}
		
		//Check if the player is blocking the exit
		checkForBlockingExit(obs, myPlayerIdx);
	}
	// Used to test violating the BlockingExitNorm.
	//int blockDoorCount = 0;
}
