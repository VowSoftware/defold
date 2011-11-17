package com.dynamo.cr.sceneed.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.osgi.util.NLS;
import org.junit.Before;
import org.junit.Test;

import com.dynamo.cr.sceneed.Messages;
import com.dynamo.cr.sceneed.core.Node;
import com.dynamo.cr.sceneed.gameobject.CollectionProxyNode;
import com.dynamo.cr.sceneed.gameobject.CollisionObjectNode;
import com.dynamo.cr.sceneed.gameobject.ComponentNode;
import com.dynamo.cr.sceneed.gameobject.ComponentPresenter;
import com.dynamo.cr.sceneed.gameobject.ComponentTypeNode;
import com.dynamo.cr.sceneed.gameobject.GameObjectNode;
import com.dynamo.cr.sceneed.gameobject.GameObjectPresenter;
import com.dynamo.cr.sceneed.gameobject.RefComponentNode;
import com.dynamo.cr.sceneed.gameobject.SpawnPointNode;
import com.dynamo.cr.sceneed.gameobject.SpriteNode;
import com.dynamo.physics.proto.Physics.CollisionObjectType;
import com.dynamo.sprite.proto.Sprite.SpriteDesc;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.protobuf.TextFormat;

public class GameObjectTest extends AbstractTest {

    class TestModule extends GenericTestModule {
        @Override
        protected void configure() {
            super.configure();
            bind(GameObjectPresenter.class).in(Singleton.class);
        }
    }

    @Override
    @Before
    public void setup() throws CoreException, IOException {
        super.setup();
        this.manager.registerNodeType("go", GameObjectNode.class, this.injector.getInstance(GameObjectPresenter.class), null);
        this.manager.registerNodeType("sprite", SpriteNode.class, this.injector.getInstance(ComponentPresenter.class), null);
        this.manager.registerNodeType("spawnpoint", SpawnPointNode.class, this.injector.getInstance(ComponentPresenter.class), null);
        this.manager.registerNodeType("collectionproxy", CollectionProxyNode.class, this.injector.getInstance(ComponentPresenter.class), null);
        this.manager.registerNodeType("collisionobject", CollisionObjectNode.class, this.injector.getInstance(ComponentPresenter.class), null);
    }

    // Tests

    @Test
    public void testLoad() throws Exception {
        String ddf = "";
        this.presenter.onLoad("go", new ByteArrayInputStream(ddf.getBytes()));
        Node root = this.model.getRoot();
        assertTrue(root instanceof GameObjectNode);
        assertTrue(this.model.getSelection().toList().contains(root));
        verify(this.view, times(1)).setRoot(root);
        verifySelection();
        verifyNoClean();
        verifyNoDirty();
    }

    @Test
    public void testAddComponent() throws Exception {
        testLoad();

        when(this.view.selectComponentType()).thenReturn("sprite");

        GameObjectNode node = (GameObjectNode)this.model.getRoot();
        GameObjectPresenter presenter = (GameObjectPresenter)this.manager.getPresenter(GameObjectNode.class);
        presenter.onAddComponent(this.presenter.getContext());
        assertEquals(1, node.getChildren().size());
        assertEquals("sprite", node.getChildren().get(0).toString());
        verifyUpdate(node);
        verifySelection();
        verifyDirty();

        undo();
        assertEquals(0, node.getChildren().size());
        verifyUpdate(node);
        verifySelection();
        verifyClean();

        redo();
        assertEquals(1, node.getChildren().size());
        assertEquals("sprite", node.getChildren().get(0).toString());
        verifyUpdate(node);
        verifySelection();
        verifyDirty();
    }

    @Test
    public void testAddComponentFromFile() throws Exception {
        testLoad();

        when(this.view.selectComponentFromFile()).thenReturn("/test.sprite");

        GameObjectNode node = (GameObjectNode)this.model.getRoot();
        GameObjectPresenter presenter = (GameObjectPresenter)this.manager.getPresenter(GameObjectNode.class);
        presenter.onAddComponentFromFile(this.presenter.getContext());
        assertEquals(1, node.getChildren().size());
        assertEquals("sprite", ((ComponentNode)node.getChildren().get(0)).getId());
        verifyUpdate(node);
        verifySelection();

        undo();
        assertEquals(0, node.getChildren().size());
        verifyUpdate(node);
        verifySelection();

        redo();
        assertEquals(1, node.getChildren().size());
        assertEquals("sprite", ((ComponentNode)node.getChildren().get(0)).getId());
        verifyUpdate(node);
        verifySelection();
    }

    @Test
    public void testRemoveComponent() throws Exception {
        testAddComponent();

        GameObjectNode node = (GameObjectNode)this.model.getRoot();
        ComponentNode component = (ComponentNode)node.getChildren().get(0);
        GameObjectPresenter presenter = (GameObjectPresenter)this.manager.getPresenter(GameObjectNode.class);

        presenter.onRemoveComponent(this.presenter.getContext());
        assertEquals(0, node.getChildren().size());
        assertEquals(null, component.getParent());
        verifyUpdate(node);
        verifySelection();

        undo();
        assertEquals(1, node.getChildren().size());
        assertEquals(component, node.getChildren().get(0));
        assertEquals(node, component.getParent());
        verifyUpdate(node);
        verifySelection();

        redo();
        assertEquals(0, node.getChildren().size());
        assertEquals(null, component.getParent());
        verifyUpdate(node);
        verifySelection();
    }

    @Test
    public void testSelection() throws Exception {
        testAddComponent();

        this.presenter.onSelect(new StructuredSelection(this.model.getRoot().getChildren().get(0)));
        verifyNoSelection();
        this.presenter.onSelect(new StructuredSelection(this.model.getRoot()));
        verifySelection();
    }

    @Test
    public void testSetId() throws Exception {
        testAddComponent();

        ComponentNode component = (ComponentNode)this.model.getRoot().getChildren().get(0);
        String oldId = component.getId();
        String newId = "sprite1";

        setNodeProperty(component, "id", newId);
        assertEquals(newId, component.getId());
        verifyUpdate(component);

        undo();
        assertEquals(oldId, component.getId());
        verifyUpdate(component);

        redo();
        assertEquals(newId, component.getId());
        verifyUpdate(component);
    }

    @Test
    public void testSave() throws Exception {
        testAddComponent();

        String path = "/test.go";
        IFile file = this.contentRoot.getFile(new Path(path));
        if (file.exists()) {
            file.delete(true, null);
        }
        OutputStream os = new FileOutputStream(file.getLocation().toFile());
        Node root = this.model.getRoot();
        this.presenter.onSave(os, null);
        file = this.contentRoot.getFile(new Path(path));
        this.model.setRoot(null);
        file.refreshLocal(0, null);
        this.presenter.onLoad(file.getFileExtension(), file.getContents());
        assertNotSame(root, this.model.getRoot());
        assertEquals(root.getClass(), this.model.getRoot().getClass());
        assertNotSame(0, this.model.getRoot().getChildren().size());
        assertEquals(root.getChildren().size(), this.model.getRoot().getChildren().size());
    }

    private void saveSprite(String path, String texture, float width, float height) throws IOException, CoreException {
        SpriteDesc.Builder builder = SpriteDesc.newBuilder();
        builder.setTexture(texture).setWidth(width).setHeight(height);
        IFile file = this.contentRoot.getFile(new Path(path));
        CharArrayWriter output = new CharArrayWriter();
        TextFormat.print(builder.build(), output);
        output.close();
        InputStream stream = new ByteArrayInputStream(output.toString().getBytes());
        if (!file.exists()) {
            file.create(stream, true, null);
        } else {
            file.setContents(stream, 0, null);
        }
    }

    /**
     * This test should be in the integrationtest in the future.
     * @throws Exception
     */
    @Test
    public void testReloadComponentFromFile() throws Exception {
        String path = "/reload.sprite";
        float width = 1.0f;
        float height = 1.0f;

        when(this.view.selectComponentFromFile()).thenReturn(path);

        saveSprite(path, "", width, height);

        testLoad();

        GameObjectNode node = (GameObjectNode)this.model.getRoot();
        GameObjectPresenter presenter = (GameObjectPresenter)this.manager.getPresenter(GameObjectNode.class);
        presenter.onAddComponentFromFile(this.presenter.getContext());
        assertEquals(1, node.getChildren().size());
        RefComponentNode component = (RefComponentNode)node.getChildren().get(0);
        assertNodePropertyStatus(component, "component", IStatus.ERROR, null);
        ComponentTypeNode type = component.getType();

        saveSprite(path, "/test.png", width, height);

        assertNodePropertyStatus(component, "component", IStatus.OK, null);
        assertEquals(component, node.getChildren().get(0));
        assertThat(type, is(not(component.getType())));
        verifyUpdate(component);
    }

    @Test
    public void testComponentMessages() throws Exception {
        testAddComponent();

        ComponentNode component = (ComponentNode)this.model.getRoot().getChildren().get(0);
        assertNodePropertyStatus(component, "id", IStatus.OK, null);

        setNodeProperty(component, "id", "");
        assertNodePropertyStatus(component, "id", IStatus.ERROR, Messages.ComponentNode_id_NOT_SPECIFIED);

        setNodeProperty(component, "id", "sprite");
        assertNodePropertyStatus(component, "id", IStatus.OK, null);

        GameObjectPresenter presenter = (GameObjectPresenter)this.manager.getPresenter(GameObjectNode.class);
        presenter.onAddComponent(this.presenter.getContext());

        component = (ComponentNode)this.model.getRoot().getChildren().get(1);
        assertNodePropertyStatus(component, "id", IStatus.ERROR, NLS.bind(Messages.ComponentNode_id_DUPLICATED, "sprite"));
    }

    @Test
    public void testComponentFromFileMessages() throws Exception {
        testAddComponentFromFile();

        RefComponentNode component = (RefComponentNode)this.model.getRoot().getChildren().get(0);
        assertNodePropertyStatus(component, "component", IStatus.ERROR, Messages.RefComponentNode_component_INVALID_REFERENCE);

        setNodeProperty(component, "component", "");
        assertNodePropertyStatus(component, "component", IStatus.INFO, Messages.SceneModel_ResourceValidator_component_NOT_SPECIFIED);

        String path = "/test.dummy";
        setNodeProperty(component, "component", path);
        assertNodePropertyStatus(component, "component", IStatus.ERROR, NLS.bind(Messages.SceneModel_ResourceValidator_component_NOT_FOUND, path));

        setNodeProperty(component, "component", "/test.tileset");
        assertNodePropertyStatus(component, "component", IStatus.ERROR, NLS.bind(Messages.RefComponentNode_component_INVALID_TYPE, "tileset"));
    }

    public void testEmbeddedComponent(String type, String[] properties, Object[] values) throws Exception {
        testLoad();

        when(this.view.selectComponentType()).thenReturn(type);

        GameObjectNode node = (GameObjectNode)this.model.getRoot();
        GameObjectPresenter presenter = (GameObjectPresenter)this.manager.getPresenter(GameObjectNode.class);
        presenter.onAddComponent(this.presenter.getContext());
        assertEquals(1, node.getChildren().size());
        Node component = node.getChildren().get(0);
        assertEquals(type, component.toString());
        verifyUpdate(node);
        verifySelection();
        verifyDirty();

        this.presenter.onSave(new ByteArrayOutputStream(), null);

        Node componentType = component.getChildren().get(0);
        for (int i = 0; i < properties.length; ++i) {
            verifyClean();
            setNodeProperty(componentType, properties[i], values[i]);
            assertNodePropertyStatus(componentType, properties[i], IStatus.OK, null);
            verifyDirty();
            undo();
        }
    }

    @Test
    public void testSprite() throws Exception {
        testEmbeddedComponent("sprite", new String[] {"texture", "width", "height", "tileWidth", "tileHeight", "tilesPerRow", "tileCount"},
                new Object[] {"test.png", 10, 10, 10, 10, 10, 10});
    }

    @Test
    public void testSpawnPoint() throws Exception {
        testEmbeddedComponent("spawnpoint", new String[] {"prototype"}, new Object[] {"test.png"});
    }

    @Test
    public void testCollectionProxy() throws Exception {
        testEmbeddedComponent("collectionproxy", new String[] {"collection"}, new Object[] {"test.png"});
    }

    @Test
    public void testCollisionObject() throws Exception {
        testEmbeddedComponent("collisionobject", new String[] {"collisionShape", "type", "mass", "friction", "restitution", "group", "mask"},
                new Object[] {"test.png", CollisionObjectType.COLLISION_OBJECT_TYPE_STATIC, 10, 10, 10, "a_group", "a_group, b_group"});
    }

    @Override
    Module getModule() {
        return new TestModule();
    }

}
