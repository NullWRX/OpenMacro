package io.github.nullwrx.openmacro.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.nullwrx.openmacro.ui.add.AddFoodScreen
import io.github.nullwrx.openmacro.ui.diary.DiaryScreen
import io.github.nullwrx.openmacro.ui.editor.FoodEditorScreen
import io.github.nullwrx.openmacro.ui.food.FoodDetailScreen
import io.github.nullwrx.openmacro.ui.onboarding.OnboardingScreen
import io.github.nullwrx.openmacro.ui.quickadd.QuickAddScreen
import io.github.nullwrx.openmacro.ui.scan.ScanMode
import io.github.nullwrx.openmacro.ui.scan.ScannerScreen
import io.github.nullwrx.openmacro.ui.settings.GoalsScreen
import io.github.nullwrx.openmacro.ui.settings.MyFoodsScreen
import io.github.nullwrx.openmacro.ui.settings.SettingsScreen
import io.github.nullwrx.openmacro.ui.weight.WeightScreen

/** Route patterns and builders. Missing numeric ids are passed as -1. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val DIARY = "diary"
    const val WEIGHT = "weight"
    const val SETTINGS = "settings"
    const val GOALS = "goals"
    const val MY_FOODS = "my_foods"
    const val ADD = "add/{date}/{meal}"
    const val FOOD = "food/{foodId}?date={date}&meal={meal}&entryId={entryId}"
    const val EDITOR = "editor?foodId={foodId}&barcode={barcode}&name={name}&brand={brand}&date={date}&meal={meal}"
    const val QUICK_ADD = "quick_add/{date}/{meal}?entryId={entryId}"
    const val SCAN = "scan/{mode}?date={date}&meal={meal}&barcode={barcode}&returnToEditor={returnToEditor}"

    fun add(date: Long, meal: Int) = "add/$date/$meal"

    fun food(foodId: Long, date: Long = -1, meal: Int = -1, entryId: Long = -1) =
        "food/$foodId?date=$date&meal=$meal&entryId=$entryId"

    fun editor(
        foodId: Long = -1,
        barcode: String? = null,
        name: String? = null,
        brand: String? = null,
        date: Long = -1,
        meal: Int = -1,
    ) = buildString {
        append("editor?foodId=$foodId&date=$date&meal=$meal")
        barcode?.let { append("&barcode=").append(Uri.encode(it)) }
        name?.let { append("&name=").append(Uri.encode(it)) }
        brand?.let { append("&brand=").append(Uri.encode(it)) }
    }

    fun quickAdd(date: Long, meal: Int, entryId: Long = -1) = "quick_add/$date/$meal?entryId=$entryId"

    fun scan(mode: ScanMode, date: Long = -1, meal: Int = -1, barcode: String? = null, returnToEditor: Boolean = false) =
        buildString {
            append("scan/${mode.name}?date=$date&meal=$meal&returnToEditor=$returnToEditor")
            barcode?.let { append("&barcode=").append(Uri.encode(it)) }
        }
}

private fun longArg(name: String) = navArgument(name) {
    type = NavType.LongType
    defaultValue = -1L
}

private fun intArg(name: String) = navArgument(name) {
    type = NavType.IntType
    defaultValue = -1
}

private fun stringArg(name: String) = navArgument(name) {
    type = NavType.StringType
    nullable = true
    defaultValue = null
}

fun NavHostController.navigateTopLevel(item: TopLevel) {
    navigate(item.route) {
        popUpTo(Routes.DIARY) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppNavHost(startOnboarding: Boolean) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = if (startOnboarding) Routes.ONBOARDING else Routes.DIARY) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onFinished = {
                nav.navigate(Routes.DIARY) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }
        composable(Routes.DIARY) {
            DiaryScreen(
                onAdd = { date, meal -> nav.navigate(Routes.add(date, meal)) },
                onOpenFoodEntry = { foodId, date, meal, entryId -> nav.navigate(Routes.food(foodId, date, meal, entryId)) },
                onOpenQuickAdd = { date, meal, entryId -> nav.navigate(Routes.quickAdd(date, meal, entryId)) },
                onScan = { date, meal -> nav.navigate(Routes.scan(ScanMode.BARCODE, date, meal)) },
                onNavigate = nav::navigateTopLevel,
            )
        }
        composable(Routes.WEIGHT) { WeightScreen(onNavigate = nav::navigateTopLevel) }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigate = nav::navigateTopLevel,
                onOpenGoals = { nav.navigate(Routes.GOALS) },
                onOpenMyFoods = { nav.navigate(Routes.MY_FOODS) },
            )
        }
        composable(Routes.GOALS) { GoalsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.MY_FOODS) {
            MyFoodsScreen(
                onBack = { nav.popBackStack() },
                onOpenFood = { id -> nav.navigate(Routes.food(id)) },
                onCreate = { nav.navigate(Routes.editor()) },
            )
        }
        composable(
            Routes.ADD,
            arguments = listOf(
                navArgument("date") { type = NavType.LongType },
                navArgument("meal") { type = NavType.IntType },
            ),
        ) {
            AddFoodScreen(
                onBack = { nav.popBackStack() },
                onOpenFood = { foodId, date, meal -> nav.navigate(Routes.food(foodId, date, meal)) },
                onScanBarcode = { date, meal -> nav.navigate(Routes.scan(ScanMode.BARCODE, date, meal)) },
                onScanLabel = { date, meal -> nav.navigate(Routes.scan(ScanMode.LABEL, date, meal)) },
                onQuickAdd = { date, meal -> nav.navigate(Routes.quickAdd(date, meal)) },
                onCreateFood = { date, meal -> nav.navigate(Routes.editor(date = date, meal = meal)) },
            )
        }
        composable(
            Routes.FOOD,
            arguments = listOf(
                navArgument("foodId") { type = NavType.LongType },
                longArg("date"),
                intArg("meal"),
                longArg("entryId"),
            ),
        ) {
            FoodDetailScreen(
                onBack = { nav.popBackStack() },
                onLogged = {
                    // Back to the diary, closing the add and scan screens on the way.
                    if (!nav.popBackStack(Routes.DIARY, inclusive = false)) nav.popBackStack()
                },
                onEdit = { foodId -> nav.navigate(Routes.editor(foodId = foodId)) },
            )
        }
        composable(
            Routes.EDITOR,
            arguments = listOf(
                longArg("foodId"),
                stringArg("barcode"),
                stringArg("name"),
                stringArg("brand"),
                longArg("date"),
                intArg("meal"),
            ),
        ) {
            FoodEditorScreen(
                onBack = { nav.popBackStack() },
                onScanLabel = { barcode -> nav.navigate(Routes.scan(ScanMode.LABEL, barcode = barcode, returnToEditor = true)) },
                onSaved = { foodId, date, meal ->
                    if (date >= 0 && meal >= 0) {
                        nav.navigate(Routes.food(foodId, date, meal)) {
                            popUpTo(Routes.EDITOR) { inclusive = true }
                        }
                    } else {
                        nav.popBackStack()
                    }
                },
            )
        }
        composable(
            Routes.QUICK_ADD,
            arguments = listOf(
                navArgument("date") { type = NavType.LongType },
                navArgument("meal") { type = NavType.IntType },
                longArg("entryId"),
            ),
        ) {
            QuickAddScreen(onBack = { nav.popBackStack() }, onSaved = {
                if (!nav.popBackStack(Routes.DIARY, inclusive = false)) nav.popBackStack()
            })
        }
        composable(
            Routes.SCAN,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType },
                longArg("date"),
                intArg("meal"),
                stringArg("barcode"),
                navArgument("returnToEditor") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) {
            ScannerScreen(
                onBack = { nav.popBackStack() },
                onFoodFound = { foodId, date, meal ->
                    nav.navigate(Routes.food(foodId, date, meal)) { popUpTo(Routes.SCAN) { inclusive = true } }
                },
                onScanLabelInstead = { barcode, date, meal ->
                    nav.navigate(Routes.scan(ScanMode.LABEL, date, meal, barcode)) { popUpTo(Routes.SCAN) { inclusive = true } }
                },
                onEnterManually = { barcode, name, brand, date, meal ->
                    nav.navigate(Routes.editor(barcode = barcode, name = name, brand = brand, date = date, meal = meal)) {
                        popUpTo(Routes.SCAN) { inclusive = true }
                    }
                },
                onLabelDone = { returnToEditor, barcode, date, meal ->
                    if (returnToEditor) {
                        nav.popBackStack()
                    } else {
                        nav.navigate(Routes.editor(barcode = barcode, date = date, meal = meal)) {
                            popUpTo(Routes.SCAN) { inclusive = true }
                        }
                    }
                },
            )
        }
    }
}
