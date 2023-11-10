package com.raja.codelfow;

import com.intellij.ide.BrowserUtil;
import com.intellij.lang.Language;
import com.intellij.mock.MockFileIndexFacade;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.ProjectFileIndexFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.impl.JavaPsiFacadeImpl;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.ProjectScopeImpl;
import com.intellij.psi.search.searches.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class SearchAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        System.out.println("SearchAction.actionPerformed");
        Project project = e.getData(CommonDataKeys.PROJECT);
        System.out.println("project = " + project);
        List<PsiModifierListOwner> componentInheretors = findAllComponentAnnotations(project);
        System.out.println("allComponents:");
        for (PsiModifierListOwner componentInheretor : componentInheretors) {
            System.out.println("annotationClass = " + componentInheretor);
            AnnotationTargetsSearch.search((PsiClass) componentInheretor).findAll().stream()
                    .filter(psiModifierListOwner -> psiModifierListOwner instanceof PsiClass)
                    .forEach(clazz -> {
                        System.out.println("\tclass " + clazz);
                        ReferencesSearch.search(clazz).findAll().stream()
                                .filter(psiReference -> psiReference instanceof PsiJavaCodeReferenceElementImpl)
                                .map(psiReference -> ((PsiJavaCodeReferenceElementImpl)psiReference).getContainingFile())
                                .flatMap(psiFile -> Arrays.stream(psiFile.getChildren()))
                                .filter(psiElement -> psiElement instanceof PsiClass)
                                .distinct()
                                .forEach(psiElement -> System.out.println("\t\treferenced in " + psiElement));
                    });

        }
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
                    .filter(psiModifierListOwner -> ((ClsClassImpl)psiModifierListOwner).getStub().isAnnotationType())
                    .toList());
        }

        return components;
    }

    @Override
    public void update(AnActionEvent e) {
        System.out.println("SearchAction.update");
        Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
        CaretModel caretModel = editor.getCaretModel();
        e.getPresentation().setEnabledAndVisible(caretModel.getCurrentCaret().hasSelection());
    }

}
