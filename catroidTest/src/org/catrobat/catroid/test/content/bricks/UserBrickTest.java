/**
 *  Catroid: An on-device visual programming system for Android devices
 *  Copyright (C) 2010-2013 The Catrobat Team
 *  (<http://developer.catrobat.org/credits>)
 *  
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *  
 *  An additional term exception under section 7 of the GNU Affero
 *  General Public License, version 3, is available at
 *  http://developer.catrobat.org/license_additional_term
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.catrobat.catroid.test.content.bricks;

import android.test.AndroidTestCase;

import com.badlogic.gdx.scenes.scene2d.actions.SequenceAction;

import org.catrobat.catroid.ProjectManager;
import org.catrobat.catroid.content.Project;
import org.catrobat.catroid.content.Script;
import org.catrobat.catroid.content.Sprite;
import org.catrobat.catroid.content.StartScript;
import org.catrobat.catroid.content.UserScript;
import org.catrobat.catroid.content.bricks.Brick;
import org.catrobat.catroid.content.bricks.ChangeXByNBrick;
import org.catrobat.catroid.content.bricks.LegoNxtMotorStopBrick;
import org.catrobat.catroid.content.bricks.LegoNxtMotorStopBrick.Motor;
import org.catrobat.catroid.content.bricks.UserBrick;
import org.catrobat.catroid.content.bricks.UserBrickUIDataArray;
import org.catrobat.catroid.content.bricks.UserScriptDefinitionBrick;
import org.catrobat.catroid.formulaeditor.Formula;
import org.catrobat.catroid.formulaeditor.FormulaElement;
import org.catrobat.catroid.formulaeditor.FormulaElement.ElementType;
import org.catrobat.catroid.test.utils.Reflection;
import org.catrobat.catroid.test.utils.Reflection.ParameterList;
import org.catrobat.catroid.test.utils.TestUtils;

import java.util.ArrayList;
import java.util.List;

public class UserBrickTest extends AndroidTestCase {
	private Sprite sprite;
	private Project project;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		sprite = new Sprite("testSprite");
		Reflection.invokeMethod(sprite, "init");

		project = new Project(null, "testProject");

		project.addSprite(sprite);
		ProjectManager.getInstance().setProject(project);
		ProjectManager.getInstance().setCurrentSprite(sprite);
	}

	@Override
	protected void tearDown() throws Exception {
		ProjectManager.getInstance().setProject(null);
		ProjectManager.getInstance().setCurrentSprite(null);
		super.tearDown();
	}

	public void testSpriteInit() {

		ArrayList<?> array = (ArrayList<?>) Reflection.getPrivateField(sprite, "userBricks");

		assertTrue("the sprite should have zero user bricks after being created and initialized.", array.size() == 0);

		Reflection.invokeMethod(sprite, "getUserBrickListAtLeastOneBrick", new ParameterList("Example", "Variable 1"));

		array = (ArrayList<?>) Reflection.getPrivateField(sprite, "userBricks");

		assertTrue("the sprite should have one user brick after getUserBrickList()", array.size() == 1);

	}

	public void testSpriteHasOneUserBrickAfterAddingAUserBrick() {
		UserBrick brick = new UserBrick(sprite, 0);
		brick.addUIText("test0");
		brick.addUIVariable("test1");

		Script userScript = TestUtils.addUserBrickToSpriteAndGetUserScript(brick, sprite);

		userScript.addBrick(new ChangeXByNBrick(sprite, 1));

		ArrayList<?> array = (ArrayList<?>) Reflection.getPrivateField(sprite, "userBricks");

		assertTrue("the sprite should have one user brick after we added a user brick to it, has " + array.size(),
				array.size() == 1);
	}

	public void testSpriteMovedCorrectly() {
		int moveValue = 6;

		UserBrick brick = new UserBrick(sprite, 0);
		brick.addUIText("test0");
		brick.addUIVariable("test1");

		Script userScript = TestUtils.addUserBrickToSpriteAndGetUserScript(brick, sprite);

		userScript.addBrick(new ChangeXByNBrick(sprite, moveValue));

		SequenceAction sequence = new SequenceAction();
		brick.addActionToSequence(sequence);

		float x = sprite.look.getXInUserInterfaceDimensionUnit();
		float y = sprite.look.getYInUserInterfaceDimensionUnit();

		assertEquals("Unexpected initial sprite x position: " + x, 0f, x);
		assertEquals("Unexpected initial sprite y position: " + y, 0f, y);

		sequence.act(1f);

		x = sprite.look.getXInUserInterfaceDimensionUnit();
		y = sprite.look.getYInUserInterfaceDimensionUnit();

		assertEquals("Unexpected initial sprite x position: ", (float) moveValue,
				sprite.look.getXInUserInterfaceDimensionUnit());
		assertEquals("Unexpected initial sprite y position: ", 0f, sprite.look.getYInUserInterfaceDimensionUnit());
	}

	public void testSpriteMovedCorrectlyWithNestedBricks() {
		Integer moveValue = 0; //TODO Forest had this line: "Integer moveValue = 6" --> why?

		UserBrick outerBrick = new UserBrick(sprite, 0);
		outerBrick.addUIText("test2");
		outerBrick.addUIVariable("outerBrickVariable");
		outerBrick.updateUIComponents(null);

		UserBrick innerBrick = new UserBrick(sprite, 1);
		innerBrick.addUIText("test0");
		innerBrick.addUIVariable("innerBrickVariable");

		Script innerScript = TestUtils.addUserBrickToSpriteAndGetUserScript(innerBrick, sprite);

		Formula innerFormula = new Formula(new FormulaElement(ElementType.USER_VARIABLE, "innerBrickVariable", null));

		innerScript.addBrick(new ChangeXByNBrick(sprite, innerFormula));

		innerBrick.updateUIComponents(null);

		Script outerScript = TestUtils.addUserBrickToSpriteAndGetUserScript(outerBrick, sprite);
		UserBrick innerBrickCopyInOuterScript = innerBrick.copyBrickForSprite(sprite, outerScript);
		outerScript.addBrick(innerBrickCopyInOuterScript);

		List<Formula> formulaList = innerBrickCopyInOuterScript.getFormulas();

		assertEquals("formulaList.size() after innerBrick.updateUIComponents()" + formulaList.size(), 1,
				formulaList.size());

		for (Formula formula : formulaList) {
			formula.setRoot(new FormulaElement(ElementType.USER_VARIABLE, "outerBrickVariable", null));
		}

		StartScript startScript = new StartScript(sprite);
		sprite.addScript(startScript);
		UserBrick outerBrickCopy = outerBrick.copyBrickForSprite(sprite, startScript);
		startScript.addBrick(outerBrickCopy);

		formulaList = outerBrickCopy.getFormulas();

		assertEquals("formulaList.size() after outerBrick.updateUIComponents()" + formulaList.size(), 1,
				formulaList.size());

		for (Formula formula : formulaList) {
			formula.setRoot(new FormulaElement(ElementType.NUMBER, moveValue.toString(), null));

			assertEquals("outerBrick.formula.interpretDouble: ", (float) moveValue, formula.interpretFloat(sprite));
		}

		SequenceAction sequence = new SequenceAction();
		startScript.run(sequence);

		float x = sprite.look.getXInUserInterfaceDimensionUnit();
		float y = sprite.look.getYInUserInterfaceDimensionUnit();

		assertEquals("Unexpected initial sprite x position: ", 0f, x);
		assertEquals("Unexpected initial sprite y position: ", 0f, y);

		sequence.act(1f);

		x = sprite.look.getXInUserInterfaceDimensionUnit();
		y = sprite.look.getYInUserInterfaceDimensionUnit();

		assertEquals("Unexpected initial sprite x position: ", (float) moveValue,
				sprite.look.getXInUserInterfaceDimensionUnit());
		assertEquals("Unexpected initial sprite y position: ", 0f, sprite.look.getYInUserInterfaceDimensionUnit());
	}

	public void testGetRequiredResources() {

		UserBrick brick = new UserBrick(sprite, 0);

		assertEquals("brick.getRequiredResources(): ", UserBrick.NO_RESOURCES, brick.getRequiredResources());

		Script userScript = TestUtils.addUserBrickToSpriteAndGetUserScript(brick, sprite);

		LegoNxtMotorStopBrick legoBrick = new LegoNxtMotorStopBrick(sprite, Motor.MOTOR_A);

		userScript.addBrick(legoBrick);

		assertNotSame("legoBrick.getRequiredResources(): ", UserBrick.NO_RESOURCES, legoBrick.getRequiredResources());

		assertEquals("brick.getRequiredResources(): ", legoBrick.getRequiredResources(), brick.getRequiredResources());

	}

	public void testDeleteBrick() {
		UserBrick outerBrick = new UserBrick(sprite, 0);
		outerBrick.addUIText("test2");
		outerBrick.addUIVariable("outerBrickVariable");
		outerBrick.updateUIComponents(null);

		UserBrick innerBrick = new UserBrick(sprite, 1);
		innerBrick.addUIText("test0");
		innerBrick.addUIVariable("innerBrickVariable");

		Script innerScript = TestUtils.addUserBrickToSpriteAndGetUserScript(innerBrick, sprite);

		Formula innerFormula = new Formula(new FormulaElement(ElementType.USER_VARIABLE, "innerBrickVariable", null));
		innerScript.addBrick(new ChangeXByNBrick(sprite, innerFormula));
		innerBrick.updateUIComponents(null);

		Script outerScript = TestUtils.addUserBrickToSpriteAndGetUserScript(outerBrick, sprite);
		Brick innerBrickCopyInOuterScript = innerBrick.copyBrickForSprite(sprite, outerScript);
		outerScript.addBrick(innerBrickCopyInOuterScript);

		StartScript startScript = new StartScript(sprite);
		sprite.addScript(startScript);
		Brick innerBrickCopy = innerBrick.copyBrickForSprite(sprite, startScript);
		startScript.addBrick(innerBrickCopy);
		Brick outerBrickCopy = outerBrick.copyBrickForSprite(sprite, startScript);
		startScript.addBrick(outerBrickCopy);

		assertEquals("Start script has wrong number of bricks before deleting.", 2, startScript.getBrickList().size());
		assertEquals("Start script has wrong brick before deleting.", innerBrickCopy, startScript.getBrick(0));

		assertEquals("outerScript has wrong number of bricks before deleting.", 1, outerScript.getBrickList().size());
		assertEquals("outerScript has wrong brick before deleting.", innerBrickCopyInOuterScript,
				outerScript.getBrick(0));

		sprite.removeUserBrick(innerBrick);

		assertEquals("Start script has wrong number of bricks after deleting.", 1, startScript.getBrickList().size());
		assertEquals("Start script has wrong brick after deleting.", outerBrickCopy, startScript.getBrick(0));

		assertEquals("outerScript has wrong number of bricks after deleting.", 0, outerScript.getBrickList().size());
	}

	public void testBrickCloneWithFormula() {
		UserBrick brick = new UserBrick(sprite, 0);
		brick.addUIText("test0");
		brick.addUIVariable("test1");

		UserBrick cloneBrick = brick.clone();
		UserBrickUIDataArray array = (UserBrickUIDataArray) Reflection.getPrivateField(brick, "uiDataArray");
		UserBrickUIDataArray clonedArray = (UserBrickUIDataArray) Reflection.getPrivateField(cloneBrick, "uiDataArray");
		assertTrue("The cloned brick has a different uiDataArray than the original brick", array == clonedArray);

		UserScriptDefinitionBrick definition = (UserScriptDefinitionBrick) Reflection.getPrivateField(brick,
				"definitionBrick");
		UserScriptDefinitionBrick clonedDef = (UserScriptDefinitionBrick) Reflection.getPrivateField(cloneBrick,
				"definitionBrick");
		assertTrue("The cloned brick has a different UserScriptDefinitionBrick than the original brick",
				definition == clonedDef);

		UserScript userScript = (UserScript) Reflection.getPrivateField(definition, "userScript");
		UserScript clonedUserScript = (UserScript) Reflection.getPrivateField(clonedDef, "userScript");
		assertTrue("The cloned brick has a different UserScriptDefinitionBrick than the original brick",
				userScript == clonedUserScript);

		ArrayList<?> componentArray = (ArrayList<?>) Reflection.getPrivateField(brick, "uiComponents");
		ArrayList<?> clonedComponentArray = (ArrayList<?>) Reflection.getPrivateField(cloneBrick, "uiComponents");
		assertTrue("The cloned brick has a different uiDataArray than the original brick",
				componentArray != clonedComponentArray);
	}
}
