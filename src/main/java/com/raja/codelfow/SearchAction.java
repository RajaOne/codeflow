package com.raja.codelfow;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.impl.JavaPsiFacadeImpl;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotationTargetsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import org.graphstream.graph.Graph;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.ui.view.Viewer;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class SearchAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        System.out.println("SearchAction.actionPerformed");
        Project project = e.getData(CommonDataKeys.PROJECT);

        System.setProperty("org.graphstream.ui", "swing");
        Graph graph = new MultiGraph("tutorial 1");
        graph.setAttribute("ui.stylesheet", "graph { }" +
                " node { text-alignment: at-right; }" +
                " edge { }");

        List<PsiModifierListOwner> componentInheretors = findAllComponentAnnotations(project);
        Set<PsiModifierListOwner> allClasses = new HashSet<>();
        for (PsiModifierListOwner componentInheretor : componentInheretors) {
            List<PsiModifierListOwner> classes = AnnotationTargetsSearch.search((PsiClass) componentInheretor).findAll().stream()
                    .filter(psiModifierListOwner -> psiModifierListOwner instanceof PsiClassImpl)
                    .toList();
            allClasses.addAll(classes);
        }
        allClasses.forEach(psiModifierListOwner -> {
            var clazz = (PsiClassImpl) psiModifierListOwner;
            graph.addNode(clazz.getName()).setAttribute("ui.label", clazz.getName());
        });
        allClasses.forEach(clazz -> {
//            System.out.println("component " + clazz);
            ReferencesSearch.search(clazz).findAll().stream()
                    .filter(psiReference -> psiReference instanceof PsiJavaCodeReferenceElementImpl)
                    .map(psiReference -> ((PsiJavaCodeReferenceElementImpl) psiReference).getContainingFile())
                    .flatMap(psiFile -> Arrays.stream(psiFile.getChildren()))
                    .filter(psiElement -> psiElement instanceof PsiClass)
                    .distinct()
                    .forEach(psiElement -> {
//                        System.out.println("\treferenced in " + psiElement);
                        var elementClass = (PsiClass) psiElement;
                        var clazzAsClass = (PsiClassImpl) clazz;
                        if (graph.getEdge(elementClass.getName() + clazzAsClass.getName()) != null) {
                            return;
                        }
                        if (graph.getNode(elementClass.getName()) == null) {
                            graph.addNode(elementClass.getName()).setAttribute("ui.label",
                                    "[" + elementClass.getName() + "]");
                        }
                        graph.addEdge(elementClass.getName() + clazzAsClass.getName(),
                                elementClass.getName(),
                                clazzAsClass.getName(),
                                true);
                    });
        });

        Viewer display = graph.display();
        display.setCloseFramePolicy(Viewer.CloseFramePolicy.CLOSE_VIEWER);
    }

    private List<PsiModifierListOwner> findAllComponentAnnotations(Project project) {
        PsiClass componentClass = JavaPsiFacadeImpl.getInstance(project).findClass("org.springframework.stereotype.Component", GlobalSearchScope.allScope(project));
        List<PsiModifierListOwner> components = new ArrayList<>();
        LinkedList<PsiModifierListOwner> queue = new LinkedList<>();
        queue.addLast(componentClass);

        while (!queue.isEmpty()) {
            PsiModifierListOwner current = queue.removeFirst();
            components.add(current);
            queue.addAll(AnnotationTargetsSearch.search((PsiClass) current).findAll()
                    .stream()
                    .filter(psiModifierListOwner -> psiModifierListOwner instanceof ClsClassImpl)
                    .filter(psiModifierListOwner -> ((ClsClassImpl) psiModifierListOwner).getStub().isAnnotationType())
                    .toList());
        }

        return components;
    }

    @Override
    public void update(AnActionEvent e) {
//        System.out.println("SearchAction.update");
//        Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
//        CaretModel caretModel = editor.getCaretModel();
        e.getPresentation().setEnabledAndVisible(true);
    }

}
