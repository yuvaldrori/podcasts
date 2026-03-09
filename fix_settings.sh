#!/bin/bash

# Update SettingsViewModelTest
sed -i 's/viewModel.errorMessage.value/viewModel.uiState.value.errorMessage/g' app/src/test/java/com/yuval/podcasts/ui/viewmodel/SettingsViewModelTest.kt
sed -i 's/importWorkInfo/uiState.importWorkInfo/g' app/src/test/java/com/yuval/podcasts/ui/viewmodel/SettingsViewModelTest.kt

