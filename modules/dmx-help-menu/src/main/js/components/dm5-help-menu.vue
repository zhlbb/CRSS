<template>
  <el-dropdown class="dm5-help-menu" size="medium" trigger="click" @command="handle">
    <el-button type="text" class="fa fa-question-circle">
      <span class="el-icon-arrow-down el-icon--right"></span>
    </el-button>
    <el-dropdown-menu slot="dropdown">
      <el-dropdown-item>
        <el-link href="https://docs.dmx.systems" target="_blank" :underline="false">使用手册</el-link>
      </el-dropdown-item>
      <el-dropdown-item>
        <el-link href="https://forum.dmx.systems" target="_blank" :underline="false">用户论坛</el-link>
      </el-dropdown-item>
      <el-dropdown-item command="openAboutBox" divided>
        关于DMX
      </el-dropdown-item>
    </el-dropdown-menu>
  </el-dropdown>
</template>

<script>
export default {
  methods: {
    handle (command) {
      if (command) {
        this.$store.dispatch(command)
      }
    }
  }
}
</script>

<style>
.dm5-help-menu {
  margin-left: 1em;
}

.dm5-help-menu .el-button {
  padding-left:  0px !important;
  padding-right: 0px !important;
}
</style>
